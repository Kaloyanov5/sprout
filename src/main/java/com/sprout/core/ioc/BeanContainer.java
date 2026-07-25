package com.sprout.core.ioc;

import com.sprout.core.annotation.*;
import com.sprout.core.exception.NoSuchBeanDefinitionException;
import com.sprout.core.exception.NoUniqueBeanDefinitionException;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BeanContainer {

    private static final Logger logger = Logger.getLogger(BeanContainer.class.getName());
    private final Map<Class<?>, Object> beans = new HashMap<>();
    private final Map<Object, Object> proxyToTarget = new HashMap<>();

    public void start(String packageName) {
        List<Class<?>> result = scan(packageName);
        if (result == null) {
            logger.warning("No discovered classes");
            return;
        }

        instantiate(result);
        inject();
    }

    public <T> T getBean(Class<T> type) {
        if (!this.beans.containsKey(type)) {
            logger.warning("Bean does not exist: " + type.getName());
            return null;
        }

        Object object = this.beans.get(type);
        if (object == null) {
            logger.warning("Bean instance for type " + type.getName() + " is null; it may have failed to instantiate, been removed, or is not managed by the container.");
            return null;
        }
        return type.cast(object);
    }

    private List<Class<?>> scan(String packageName) {
        // step 1 - scan classpath
        List<Class<?>> result = new ArrayList<>();
        String resourcePath = packageName.replace(".", "/");
        Enumeration<URL> urls;
        try {
            urls = Thread.currentThread().getContextClassLoader().getResources(resourcePath);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to resolve classpath resources for: " + resourcePath, e);
            return null;
        }
        if (!urls.hasMoreElements()) return null;

        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            try {
                File packageFile = Paths.get(url.toURI()).toFile();
                walk(packageFile, packageName, result);
            } catch (URISyntaxException e) {
                logger.warning("Package not found: " + resourcePath);
            }
        }
        return result;
    }

    private void walk(File directory, String currentPackage, List<Class<?>> result) {
        if (directory == null) return;
        File[] files = directory.listFiles();

        if (files == null) return;
        for (File file : files) {
            if (file.getName().endsWith(".class")) {
                String fullyQualifiedClassName = currentPackage + "." + file.getName().replace(".class", "");
                try {
                    result.add(Class.forName(fullyQualifiedClassName));
                } catch (ClassNotFoundException e) {
                    logger.warning("Class not found: " + fullyQualifiedClassName);
                }
                continue;
            }
            String subDirectory = currentPackage + "." + file.getName();
            walk(file, subDirectory, result);
        }
    }

    private void instantiate(List<Class<?>> discoveredClasses) {
        for (Class<?> clazz : discoveredClasses) {
            if (isIneligibleForWiring(clazz)) {
                logger.info("Class is ineligible for wiring: " + clazz.getName());
                continue;
            }
            try {
                Object target = clazz.getDeclaredConstructor().newInstance();
                Object finalBean = target;
                if (needsAop(clazz)) {
                    if (clazz.getInterfaces().length == 0) {
                        logger.warning("No interface for the proxy to implement for class:" + clazz.getName());
                        this.beans.put(clazz, target);
                    } else {
                        Object proxy = Proxy.newProxyInstance(
                                target.getClass().getClassLoader(),
                                target.getClass().getInterfaces(),
                                new AspectInterceptor(target)
                        );
                        finalBean = proxy;
                        proxyToTarget.put(proxy, target);
                    }
                } else {
                    this.beans.put(clazz, target);
                }
                for (Class<?> implInterface : clazz.getInterfaces()) {
                    Object beanValue = this.beans.putIfAbsent(implInterface, finalBean);
                    if (beanValue != null) {
                        throw new NoUniqueBeanDefinitionException(implInterface + " is claimed by both " + beanValue + " and " + finalBean);
                    }
                }
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                     InvocationTargetException e) {
                logger.log(Level.WARNING, "Failed to instantiate bean: " + clazz.getName(), e);
            }
        }
    }

    private void inject() {
        for (Object bean : this.beans.values()) {
            bean = proxyToTarget.getOrDefault(bean, bean);

            Field[] beanFields = bean.getClass().getDeclaredFields();
            for (Field field : beanFields) {
                if (!field.isAnnotationPresent(Wire.class)) continue;
                Class<?> fieldType = field.getType();
                if (!this.beans.containsKey(fieldType)) {
                    throw new NoSuchBeanDefinitionException(field.getName() + " " + fieldType + " " + field.getClass());
                }
                try {
                    field.setAccessible(true);
                    field.set(bean, this.beans.get(fieldType));
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    logger.log(Level.WARNING, "Failed to inject field: " + field.getName(), e);
                }
            }
        }
    }

    private boolean isIneligibleForWiring(Class<?> clazz) {
        return clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers()) || !clazz.isAnnotationPresent(Wireable.class);
    }

    private boolean needsAop(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods()).anyMatch(m ->
                (m.isAnnotationPresent(Logged.class) || interfaceAnnotationFallback(clazz, m, Logged.class)) ||
                (m.isAnnotationPresent(MyTransactional.class) || interfaceAnnotationFallback(clazz, m, MyTransactional.class)) ||
                (m.isAnnotationPresent(MyRetry.class) || interfaceAnnotationFallback(clazz, m, MyRetry.class))
        );
    }

    private boolean isExactMethod(Method m1, Method m2) {
        if (!m1.getName().equals(m2.getName()))
            return false;

        return Arrays.equals(m1.getParameterTypes(), m2.getParameterTypes());
    }

    private boolean interfaceAnnotationFallback(Class<?> clazz, Method m1, Class<? extends Annotation> annotation) {
        return Arrays.stream(clazz.getInterfaces()
                ).anyMatch(c ->
                Arrays.stream(c.getDeclaredMethods()
                ).anyMatch(m2 -> isExactMethod(m1, m2) && m2.isAnnotationPresent(annotation))
        );
    }
}
