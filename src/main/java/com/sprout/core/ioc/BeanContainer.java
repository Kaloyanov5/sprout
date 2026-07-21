package com.sprout.core.ioc;

import com.sprout.core.annotation.Wire;
import com.sprout.core.annotation.Wireable;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BeanContainer {

    private static final Logger logger = Logger.getLogger(BeanContainer.class.getName());
    private final Map<Class<?>, Object> beans = new HashMap<>();

    public void start(String packageName) {
        List<Class<?>> result = scan(packageName);
        if (result == null) {
            logger.warning("No discovered classes");
            return;
        }

        instantiate(result);
        inject();
    }

    private List<Class<?>> scan(String packageName) {
        // step 1 - scan classpath
        List<Class<?>> result = new ArrayList<>();
        String resourcePath = packageName.replace(".", "/");
        URL url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        if (url == null) return null;
        File packageFile = null;
        try {
            packageFile = Paths.get(url.toURI()).toFile();
        } catch (URISyntaxException e) {
            logger.warning("Package not found: " + resourcePath);
        }

        walk(packageFile, packageName, result);
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
        for (Class<?> discClass : discoveredClasses) {
            if (isIneligibleForWiring(discClass)) {
                logger.warning("Class is ineligible for wiring: " + discClass.getName());
                continue;
            }
            try {
                Constructor<?> constructor = discClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                this.beans.put(
                        discClass,
                        constructor.newInstance()
                );
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                     InvocationTargetException e) {
                logger.log(Level.WARNING, "Failed to instantiate bean: " + discClass.getName(), e);
            }
        }
    }

    private void inject() {
        for (Map.Entry<Class<?>, Object> bean : this.beans.entrySet()) {
            Field[] beanFields = bean.getKey().getDeclaredFields();
            for (Field field : beanFields) {
                if (!field.isAnnotationPresent(Wire.class)) continue;
                Class<?> fieldType = field.getType();
                if (!this.beans.containsKey(fieldType)) {
                    logger.warning("Dependency not found: " + fieldType);
                    continue;
                }
                try {
                    field.setAccessible(true);
                    field.set(bean.getValue(), this.beans.get(fieldType));
                } catch (IllegalAccessException e) {
                    logger.log(Level.WARNING, "Failed to inject field: " + field.getName(), e);
                }
            }
        }
    }

    private boolean isIneligibleForWiring(Class<?> clazz) {
        return clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers()) || !clazz.isAnnotationPresent(Wireable.class);
    }
}
