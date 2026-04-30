package com.green.imagecore.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PipelineStepDto {
    private Long id;
    private int stepOrder;
    private Long toolId;
    private String toolName;
    private Long inputImageId;
    private Long analysisJobId;
    private Long outputImageId;
    private String status;
}
