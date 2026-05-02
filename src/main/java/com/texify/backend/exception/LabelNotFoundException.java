package com.texify.backend.exception;

public class LabelNotFoundException extends RuntimeException {

    public LabelNotFoundException(Long id) {
        super("Label not found: " + id);
    }
}
