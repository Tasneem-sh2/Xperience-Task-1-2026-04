package com.xperience.hero.exception;

public class InvalidEventStateTransitionException extends RuntimeException {

    public InvalidEventStateTransitionException(String message) {
        super(message);
    }
}
