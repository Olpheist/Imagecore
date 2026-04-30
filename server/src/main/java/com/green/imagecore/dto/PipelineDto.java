package com.green.imagecore.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PipelineDto {
    private Long id;
    private Long imageId;
    private String status;
    private String createdAt;
    private List<PipelineStepDto> steps;
}
