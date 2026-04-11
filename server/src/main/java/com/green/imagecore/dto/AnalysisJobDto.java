package com.green.imagecore.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalysisJobDto {
    private Long id;
    private Long imageId;
    private Long toolId;
    private String toolName;
    private String status;
    private String ecsTaskArn;
    private String createdAt;
}
