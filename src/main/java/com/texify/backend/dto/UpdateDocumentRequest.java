package com.texify.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDocumentRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String blocks;

    private Boolean isPublic;

    private Boolean pinned;

    private String icon;

    private String color;

    private Integer wordCount;

    private Integer equationCount;

    private Integer figureCount;

    private Integer plotCount;

    private Integer codeCount;

    private Integer compilationCount;
}
