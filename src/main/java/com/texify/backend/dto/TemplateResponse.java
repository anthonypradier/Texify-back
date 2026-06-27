package com.texify.backend.dto;

import com.texify.backend.entity.PreviewStatus;

import java.time.LocalDateTime;

/**
 * API view of a {@code Template}.
 *
 * @param previewPdfPath   raw stored path of the preview PDF (kept for backward compatibility)
 * @param previewImageUrl  full public URL of the PNG preview image (nullable)
 * @param previewPdfUrl    full public URL of the preview PDF (nullable)
 * @param previewStatus    current preview lifecycle state
 */
public record TemplateResponse(
        Long id,
        String title,
        String description,
        String blocks,
        String previewPdfPath,
        String previewImageUrl,
        String previewPdfUrl,
        PreviewStatus previewStatus,
        String icon,
        String color,
        String category,
        boolean isSystem,
        LocalDateTime createdAt
) {}
