package com.texify.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDocumentRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String icon;

    private String color;

    // Boolean wrapper so Jackson maps "isPublic" → getIsPublic()/setIsPublic()
    private Boolean isPublic;
}
