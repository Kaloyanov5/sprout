package com.sprout.core.exception;

public class NoUniqueBeanDefinitionException extends RuntimeException {
    public NoUniqueBeanDefinitionException(String message) {
        super(message);
    }
}
