package com.texify.backend.exception;

public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(Long id) {
        super("Template not found: " + id);
    }
}
