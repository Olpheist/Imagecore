package com.green.imagecore.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Data Transfer Object (Dto) for the tool object
 */
@Getter
@Setter
public class ToolDto {
    private Long toolId;
    private Long CreatedByUserId;
    private String name;
    private String category;
    private String description;
    private String imageTag;
}