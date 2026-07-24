package com.sprout.core.ioc;

import com.sprout.core.annotation.Logged;

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
        boolean logged = target.getClass().getMethod(method.getName(), method.getParameterTypes()).isAnnotationPresent(Logged.class);

        if (logged)
            logger.info("--- [LOG] Starting method " + method.getName() + " ---");

        Object response;
        try {
            response = method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }

        if (logged)
            logger.info("--- [LOG] Finished method " + method.getName() + " ---");

        return response;
    }
}
