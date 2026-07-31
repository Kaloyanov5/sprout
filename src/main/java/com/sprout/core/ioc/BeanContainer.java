package com.sprout.core.ioc;

import com.sprout.core.annotation.*;
import com.sprout.core.exception.NoSuchBeanDefinitionException;
import com.sprout.core.exception.NoUniqueBeanDefinitionException;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class BeanContainer {

    private static final Logger logger = Logger.getLogger(BeanContainer.class.getName());
    private final Map<Class<?>, Object> beans = new HashMap<>();
    private final Map<Object, Object> proxyToTarget = new HashMap<>();

    public void start(String packageName) {
        List<Class<?>> result = scan(packageName);
        instantiate(result);
        inject();
    }

    public <T> T getBean(Class<T> type) {
        if (!this.beans.containsKey(type)) {
            throw new NoSuchBeanDefinitionException("No bean of type " + type.getName() + " is registered in the container.");
        }

        Object object = this.beans.get(type);
        if (object == null) {
            throw new NoSuchBeanDefinitionException("Bean instance for type " + type.getName() + " is null; it may have failed to instantiate, been removed, or is not managed by the container.");
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
            throw new IllegalStateException("Failed to scan package: " + packageName, e);
        }
        if (!urls.hasMoreElements()) throw new IllegalStateException("Package not found: " + packageName);

        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String urlProtocol = url.getProtocol();

            switch (urlProtocol) {
                case "file" -> {
                    try {
                        File packageFile = Paths.get(url.toURI()).toFile();
                        walk(packageFile, packageName, result);
                    } catch (URISyntaxException e) {
                        logger.warning("Package not found: " + resourcePath);
                    }
                }
                case "jar" -> {
                    try {
                        Enumeration<JarEntry> jarEntries = ((JarURLConnection) url.openConnection()).getJarFile().entries();
                        while (jarEntries.hasMoreElements()) {
                            JarEntry jarEntry = jarEntries.nextElement();
                            String jarEntryName = jarEntry.getName();
                            if (jarEntryName.startsWith(resourcePath + "/") && jarEntryName.endsWith(".class")) {
                                String fullyQualifiedJarName = jarEntryName.substring(0, jarEntryName.length() - ".class".length()).replace('/', '.');
                                registerClass(fullyQualifiedJarName, result);
                            }
                        }
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Failed to scan JAR entries for package '" + packageName + "' from URL: " + url, e);
                    }
                }
                default -> logger.warning("Unsupported URL protocol '" + urlProtocol + "' while scanning package: " + resourcePath + " (URL: " + url + ")");
            }

        }
        return result;
    }

    private void walk(File directory, String currentPackage, List<Class<?>> result) {
        if (directory == null) return;
        File[] files = directory.listFiles();

        if (files == null) return;
        for (File file : files) {
            String fileName = file.getName();
            if (fileName.endsWith(".class")) {
                String fullyQualifiedClassName = currentPackage + "." + fileName.substring(0, fileName.length() - ".class".length());
                registerClass(fullyQualifiedClassName, result);
                continue;
            }
            String subDirectory = currentPackage + "." + fileName;
            walk(file, subDirectory, result);
        }
    }

    private void instantiate(List<Class<?>> discoveredClasses) {
        for (Class<?> clazz : discoveredClasses) {
            if (isIneligibleForWiring(clazz)) {
                logger.info("Class is ineligible for wiring: " + clazz.getName());
                continue;
            }

            boolean requiresAop = needsAop(clazz);
            boolean jdkProxy = requiresAop && useJdkProxy(clazz);
            if (requiresAop) {
                String problem = proxyabilityProblem(clazz);
                if (problem != null)
                    throw new IllegalStateException("AOP required but class cannot be proxied for " + clazz.getName() + ": " + problem);
            }

            try {
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object target = constructor.newInstance();
                Object finalBean = target;
                if (requiresAop) {
                    Object proxy;
                    if (jdkProxy) {
                        // JDK Proxy
                        proxy = Proxy.newProxyInstance(
                                target.getClass().getClassLoader(),
                                target.getClass().getInterfaces(),
                                new AspectInterceptor(target)
                        );

                    } else {
                        // ByteBuddy subclass proxy
                        proxy = new ByteBuddy()
                                .subclass(clazz)
                                .method(any().and(not(isDeclaredBy(Object.class))))
                                .intercept(InvocationHandlerAdapter.of(new AspectInterceptor(target)))
                                .make()
                                .load(clazz.getClassLoader())
                                .getLoaded().getDeclaredConstructor().newInstance();
                    }
                    finalBean = proxy;
                    if (clazz.isInstance(proxy)) this.beans.put(clazz, proxy);
                    proxyToTarget.put(proxy, target);
                } else {
                    this.beans.put(clazz, target);
                }
                for (Class<?> implInterface : clazz.getInterfaces()) {
                    if (implInterface.getMethods().length == 0) continue;
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
        Set<Object> beanSet = Collections.newSetFromMap(new IdentityHashMap<>());
        this.beans.values().forEach(bean -> beanSet.add(proxyToTarget.getOrDefault(bean, bean)));
        for (Object bean : beanSet) {
            List<Field> beanFields = new ArrayList<>();
            Class<?> currentClass = bean.getClass();
            while (currentClass != null) {
                beanFields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
                currentClass = currentClass.getSuperclass();
            }

            for (Field field : beanFields) {
                if (!field.isAnnotationPresent(Wire.class)) continue;
                Class<?> fieldType = resolveBeanType(field);
                try {
                    field.setAccessible(true);
                    field.set(bean, this.beans.get(fieldType));
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    logger.log(Level.WARNING, "Failed to inject field: " + field.getName(), e);
                }
            }
        }
    }

    private Class<?> resolveBeanType(Field field) {
        Class<?> fieldType = field.getType();
        if (!this.beans.containsKey(fieldType)) {
            throw new NoSuchBeanDefinitionException(
                    "No bean of type " + fieldType.getName()
                            + " found for @Wire field '" + field.getName()
                            + "' in " + field.getDeclaringClass().getName()
                            + ". Is " + fieldType.getSimpleName() + " annotated with @Wireable?");
        }
        return fieldType;
    }

    private boolean isIneligibleForWiring(Class<?> clazz) {
        return clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers()) || !clazz.isAnnotationPresent(Wireable.class);
    }

    private boolean needsAop(Class<?> clazz) {
        return !findAopMethods(clazz).isEmpty();
    }

    /**
     * Returns null when proxying is possible, otherwise a brief explanation why proxying is impossible.
     */
    private String proxyabilityProblem(Class<?> clazz) {
        boolean jdk = useJdkProxy(clazz);
        if (!jdk) {
            if (Modifier.isFinal(clazz.getModifiers())) {
                return "class is final (cannot subclass)";
            }

            try {
                int mods = clazz.getDeclaredConstructor().getModifiers();
                if (Modifier.isPrivate(mods)) {
                    return "no-arg constructor is private and cannot be invoked by a subclass";
                }
                if (!Modifier.isPublic(mods) && !Modifier.isProtected(mods)) {
                    return "no-arg constructor is package-private; the generated subclass is defined in a separate classloader, so it is not in the same runtime package";
                }
            } catch (NoSuchMethodException e) {
                return "no no-arg constructor";
            }

            for (Method m : findAopMethods(clazz)) {
                int mods = m.getModifiers();
                if (Modifier.isFinal(mods) || Modifier.isPrivate(mods) || Modifier.isStatic(mods)) {
                    return "annotated method '" + m.getName() + "' is final/private/static and cannot be intercepted by subclassing";
                }
                if (!Modifier.isPublic(mods) && !Modifier.isProtected(mods)) {
                    return "annotated method '" + m.getName() + "' is package-private; the generated subclass is defined in a separate classloader, so it cannot override it";
                }
            }
        }
        return null;
    }

    private boolean interfaceMethodExists(Class<?> clazz, Method method) {
        for (Class<?> iface : clazz.getInterfaces()) {
            for (Method im : iface.getDeclaredMethods()) {
                if (isExactMethod(method, im)) return true;
            }
            if (interfaceMethodExists(iface, method)) return true;
        }

        Class<?> superclass = clazz.getSuperclass();
        return superclass != null && interfaceMethodExists(superclass, method);
    }

    private List<Method> findAopMethods(Class<?> clazz) {
        Class<?> current = clazz;
        List<Method> aopMethods = new ArrayList<>();
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (isAopMethod(clazz, m)) {
                    aopMethods.add(m);
                }
            }
            current = current.getSuperclass();
        }
        return aopMethods;
    }

    private boolean useJdkProxy(Class<?> clazz) {
        List<Method> aopMethods = findAopMethods(clazz);
        return clazz.getInterfaces().length > 0
                && !aopMethods.isEmpty()
                && aopMethods.stream().allMatch(m -> interfaceMethodExists(clazz, m));
    }

    private boolean isAopMethod(Class<?> rootClass, Method method) {
        for (Class<? extends Annotation> ann : Aop.ANNOTATIONS) {
            if (method.isAnnotationPresent(ann) || interfaceAnnotationFallback(rootClass, method, ann)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExactMethod(Method m1, Method m2) {
        if (!m1.getName().equals(m2.getName()))
            return false;

        return Arrays.equals(m1.getParameterTypes(), m2.getParameterTypes());
    }

    private boolean interfaceAnnotationFallback(Class<?> clazz, Method method, Class<? extends Annotation> annotation) {
        for (Class<?> clazzInterface : clazz.getInterfaces()) {
            for (Method interfaceMethod : clazzInterface.getDeclaredMethods()) {
                if (isExactMethod(method, interfaceMethod) && interfaceMethod.isAnnotationPresent(annotation))
                    return true;
            }

            if (interfaceAnnotationFallback(clazzInterface, method, annotation)) return true;
        }

        Class<?> superclass = clazz.getSuperclass();
        return superclass != null && interfaceAnnotationFallback(superclass, method, annotation);
    }

    private void registerClass(String fullyQualifiedName, List<Class<?>> result) {
        try {
            result.add(Class.forName(fullyQualifiedName, false, Thread.currentThread().getContextClassLoader()));
        } catch (ClassNotFoundException | LinkageError e) {
            logger.log(Level.WARNING, "Skipping class: " + fullyQualifiedName, e);
        }
    }
}
