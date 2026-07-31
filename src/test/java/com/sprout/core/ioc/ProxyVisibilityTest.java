package com.sprout.core.ioc;

import com.sprout.core.ioc.visibility.allowed.ProtectedCtorBean;
import com.sprout.core.ioc.visibility.allowed.ProtectedMethodBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the member visibility rules in BeanContainer.proxyabilityProblem.
 *
 * Subclass proxies are loaded with ClassLoadingStrategy.Default.WRAPPER, which defines the generated
 * class in a child classloader. Package-private members therefore sit in a different runtime package
 * than the proxy and are unusable, even though the package names match. Rejecting them up front is
 * what stops a bean from silently registering with its aspects dropped.
 *
 * Each fixture lives in its own package because start() scans recursively and aborts on the first
 * unproxyable bean it finds.
 */
class ProxyVisibilityTest {

    private static final String FIXTURES = "com.sprout.core.ioc.visibility.";

    @Test
    void proxiesBeanWithProtectedConstructor() {
        BeanContainer container = new BeanContainer();
        container.start(FIXTURES + "allowed");

        ProtectedCtorBean bean = container.getBean(ProtectedCtorBean.class);

        assertNotSame(ProtectedCtorBean.class, bean.getClass(), "bean should be a generated subclass proxy");
        assertEquals("ok", bean.run());
    }

    @Test
    void advisesProtectedMethod() throws Throwable {
        BeanContainer container = new BeanContainer();
        container.start(FIXTURES + "allowed");

        ProtectedMethodBean bean = container.getBean(ProtectedMethodBean.class);

        // run() is protected, so it is unreachable from this package; the reflective call still
        // dispatches virtually to the proxy's override.
        Method run = ProtectedMethodBean.class.getDeclaredMethod("run");
        run.setAccessible(true);

        List<String> advice = captureAspectLog(() -> run.invoke(bean));

        assertTrue(advice.stream().anyMatch(message -> message.contains("Starting method run")),
                "@Logged should advise a protected method, but the interceptor logged: " + advice);
    }

    @Test
    void rejectsPackagePrivateConstructor() {
        assertNotProxyable("packageprivatector", "no-arg constructor is package-private");
    }

    @Test
    void rejectsPrivateConstructor() {
        assertNotProxyable("privatector", "no-arg constructor is private");
    }

    @Test
    void rejectsPackagePrivateAnnotatedMethod() {
        assertNotProxyable("packageprivatemethod", "annotated method 'run' is package-private");
    }

    @Test
    void rejectsFinalAnnotatedMethod() {
        assertNotProxyable("finalmethod", "annotated method 'run' is final/private/static");
    }

    @Test
    void rejectsFinalClass() {
        assertNotProxyable("finalclass", "class is final");
    }

    private void assertNotProxyable(String fixturePackage, String expectedReason) {
        BeanContainer container = new BeanContainer();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> container.start(FIXTURES + fixturePackage));

        assertTrue(error.getMessage().contains(expectedReason),
                "expected rejection reason '" + expectedReason + "' but was: " + error.getMessage());
    }

    private List<String> captureAspectLog(Executable action) throws Throwable {
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        Logger logger = Logger.getLogger(AspectInterceptor.class.getName());
        logger.addHandler(handler);
        try {
            action.execute();
        } finally {
            logger.removeHandler(handler);
        }
        return messages;
    }
}
