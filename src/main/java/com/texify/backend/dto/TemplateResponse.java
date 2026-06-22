package com.texify.backend.dto;

import java.time.LocalDateTime;

public record TemplateResponse(
        Long id,
        String title,
        String description,
        String blocks,
        String previewPdfPath,
        String icon,
        String color,
        String category,
        boolean isSystem,
        LocalDateTime createdAt
) {}
