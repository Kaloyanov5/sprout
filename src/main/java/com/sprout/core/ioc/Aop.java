package com.sprout.core.ioc;

import com.sprout.core.annotation.Logged;
import com.sprout.core.annotation.Retried;
import com.sprout.core.annotation.Transacted;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Centralized set of AOP-related annotations to avoid duplication.
 */
public final class Aop {
    public static final Set<Class<? extends Annotation>> ANNOTATIONS = Set.of(
            Logged.class,
            Transacted.class,
            Retried.class
    );

    private Aop() {}
}
