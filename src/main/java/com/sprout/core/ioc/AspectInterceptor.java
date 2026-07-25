package com.sprout.core.ioc;

import com.sprout.core.annotation.Logged;
import com.sprout.core.annotation.MyRetry;
import com.sprout.core.annotation.MyTransactional;

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

        Method targetMethod;
        try {
            targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Cannot find " + method.getName() + " on " + target.getClass(), e);
        }

        boolean logged = resolveAnnotation(method, targetMethod, Logged.class) != null;
        boolean transactional = resolveAnnotation(method, targetMethod, MyTransactional.class) != null;
        boolean isTransactionActive = false;
        MyRetry retryable = resolveAnnotation(method, targetMethod, MyRetry.class);

        if (logged)
            logger.info("--- [LOG] Starting method " + method.getName() + " ---");

        if (transactional) {
            logger.info("--- BEGIN TX ---");
            isTransactionActive = true;
        }

        try {
            Object response = null;
            if (retryable != null) {
                int times = Math.max(1, retryable.times());
                Throwable exception = null;

                for (int i = 0; i < times; i++) {
                    if (!isTransactionActive && transactional) {
                        isTransactionActive = true;
                        logger.info("--- BEGIN TX ---");
                    }
                    try {
                        response = method.invoke(target, args);
                        exception = null;
                        break;
                    } catch (InvocationTargetException e) {
                        exception = e;
                        if (transactional) {
                            isTransactionActive = false;
                            logger.info("--- ROLLBACK TX ---");
                        }
                        if (i < times - 1)
                            logger.info("--- Retrying method... (Attempt " + (i + 1) + "/" + times + ") ---");
                    }
                }

                if (exception != null)
                    throw exception;
            } else {
                response = method.invoke(target, args);
            }

            if (transactional)
                logger.info("--- COMMIT TX ---");

            if (logged)
                logger.info("--- [LOG] Finished method " + method.getName() + " ---");

            return response;
        } catch (Throwable e) {
            if (logged)
                logger.info("--- [LOG] Exception thrown by: " + method.getName() + " ---");
            if (transactional && isTransactionActive)
                logger.info("--- ROLLBACK TX ---");
            throw e instanceof InvocationTargetException ? ((InvocationTargetException) e).getTargetException() : e;
        }
    }

    private <T extends Annotation> T resolveAnnotation(Method method, Method targetMethod, Class<T> annotationClass) {
        T annotation = targetMethod.getAnnotation(annotationClass);

        annotation = annotation == null
                ? method.getAnnotation(annotationClass)
                : annotation;

        return annotation;
    }
}
