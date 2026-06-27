package com.texify.backend.dto;

import com.texify.backend.entity.PreviewStatus;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentResponse(
        Long id,
        String title,
        String blocks,
        boolean isPublic,
        String compilePdfPath,
        int wordCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean pinned,
        String icon,
        String color,
        int equationCount,
        int figureCount,
        int plotCount,
        int codeCount,
        int compilationCount,
        String previewImageUrl,
        PreviewStatus previewStatus,
        List<LabelResponse> labels
) {}
