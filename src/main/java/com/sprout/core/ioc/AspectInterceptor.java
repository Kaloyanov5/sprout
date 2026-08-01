package com.sprout.core.ioc;

import com.sprout.core.annotation.Logged;
import com.sprout.core.annotation.Retried;
import com.sprout.core.annotation.Transacted;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public class AspectInterceptor implements InvocationHandler {

    private static final Logger logger = Logger.getLogger(AspectInterceptor.class.getName());
    private final Object target;

    public AspectInterceptor(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> method.invoke(target, args);
                };
            }
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
        Method targetMethod = resolveTargetMethod(method);

        boolean logged = resolveAnnotation(method, targetMethod, Logged.class) != null;
        boolean transactional = resolveAnnotation(method, targetMethod, Transacted.class) != null;
        TransactionScope transaction = transactional ? new TransactionScope() : null;
        Retried retryable = resolveAnnotation(method, targetMethod, Retried.class);

        if (logged)
            logger.info("--- [LOG] Starting method " + method.getName() + " ---");

        if (transaction != null) transaction.begin();

        try {
            Object response = null;
            if (retryable != null) {
                int times = Math.max(1, retryable.times());
                Throwable exception = null;

                for (int i = 0; i < times; i++) {
                    if (i > 0 && transaction != null) transaction.begin();
                    try {
                        response = targetMethod.invoke(target, args);
                        exception = null;
                        break;
                    } catch (InvocationTargetException e) {
                        exception = e;
                        if (i < times - 1) {
                            if (transaction != null) transaction.rollback();
                            logger.info("--- Retrying method... (Attempt " + (i + 1) + "/" + times + ") ---");
                        }
                    }
                }

                if (exception != null)
                    throw exception;
            } else {
                response = targetMethod.invoke(target, args);
            }

            if (transaction != null) transaction.commit();

            if (logged)
                logger.info("--- [LOG] Finished method " + method.getName() + " ---");

            return response;
        } catch (Throwable e) {
            if (logged)
                logger.info("--- [LOG] Exception thrown by: " + method.getName() + " ---");
            if (transaction != null) transaction.rollback();
            throw e instanceof InvocationTargetException ? ((InvocationTargetException) e).getTargetException() : e;
        }
    }

    /**
     * Resolves the advised method on the target, walking up the hierarchy. {@link Class#getMethod} is
     * public-only, so it misses the protected methods a subclass proxy is able to intercept.
     */
    private Method resolveTargetMethod(Method m) {
        Class<?> current = target.getClass();
        Method targetMethod;
        while (current != null) {
            try {
                targetMethod = current.getDeclaredMethod(m.getName(), m.getParameterTypes());
                targetMethod.setAccessible(true);
                return targetMethod;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        try {
            targetMethod = target.getClass().getMethod(m.getName(), m.getParameterTypes());
            targetMethod.setAccessible(true);
            return targetMethod;
        } catch (NoSuchMethodException ignored) { }
        throw new IllegalStateException("Cannot find " + m.getName() + " on " + target.getClass());
    }

    private <T extends Annotation> T resolveAnnotation(Method method, Method targetMethod, Class<T> annotationClass) {
        T annotation = targetMethod.getAnnotation(annotationClass);

        annotation = annotation == null
                ? method.getAnnotation(annotationClass)
                : annotation;

        return annotation;
    }
}
