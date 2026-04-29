package com.green.imagecore.mapper;

import com.green.imagecore.dto.PipelineDto;
import com.green.imagecore.dto.PipelineStepDto;
import com.green.imagecore.entities.Pipeline;
import com.green.imagecore.entities.PipelineStep;

import java.util.ArrayList;
import java.util.List;

public class PipelineMapper {

    public static PipelineDto toDto(Pipeline pipeline, List<PipelineStep> steps) {
        PipelineDto dto = new PipelineDto();
        dto.setId(pipeline.getId());
        dto.setImageId(pipeline.getImage().getId());
        dto.setStatus(pipeline.getStatus().name());
        dto.setCreatedAt(pipeline.getCreatedAt() != null ? pipeline.getCreatedAt().toString() : null);

        // Thread the input image ID through steps: step 0 reads the original image,
        // each subsequent step reads the previous step's output image.
        Long prevImageId = pipeline.getImage().getId();
        List<PipelineStepDto> stepDtos = new ArrayList<>();
        for (PipelineStep step : steps) {
            PipelineStepDto stepDto = toStepDto(step, prevImageId);
            stepDtos.add(stepDto);
            if (step.getOutputImage() != null) {
                prevImageId = step.getOutputImage().getId();
            }
        }
        dto.setSteps(stepDtos);
        return dto;
    }

    private static PipelineStepDto toStepDto(PipelineStep step, Long inputImageId) {
        PipelineStepDto dto = new PipelineStepDto();
        dto.setId(step.getId());
        dto.setStepOrder(step.getStepOrder());
        dto.setToolId(step.getTool().getToolId());
        dto.setToolName(step.getTool().getName());
        dto.setInputImageId(inputImageId);
        dto.setAnalysisJobId(step.getAnalysisJob() != null ? step.getAnalysisJob().getId() : null);
        dto.setOutputImageId(step.getOutputImage() != null ? step.getOutputImage().getId() : null);
        dto.setStatus(step.getStatus().name());
        return dto;
    }
}
