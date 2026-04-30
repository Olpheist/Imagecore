package com.green.imagecore.mapper;

import com.green.imagecore.dto.PipelineDto;
import com.green.imagecore.entities.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PipelineMapperTest {

    // helpers

    private Pipeline buildPipeline(Long id, Long imageId, PipelineStatus status, Instant createdAt) {
        DicomImage image = new DicomImage();
        image.setId(imageId);

        User user = new User();
        user.setId(1L);

        Pipeline pipeline = new Pipeline();
        pipeline.setId(id);
        pipeline.setImage(image);
        pipeline.setUser(user);
        pipeline.setStatus(status);
        pipeline.setCreatedAt(createdAt);
        pipeline.setUpdatedAt(Instant.now());
        return pipeline;
    }

    private Tool buildTool(Long toolId, String name) {
        Tool tool = new Tool();
        tool.setToolId(toolId);
        tool.setName(name);
        return tool;
    }

    private PipelineStep buildStep(Long id, Pipeline pipeline, int order, Tool tool, PipelineStatus status) {
        PipelineStep step = new PipelineStep();
        step.setId(id);
        step.setPipeline(pipeline);
        step.setStepOrder(order);
        step.setTool(tool);
        step.setStatus(status);
        step.setUpdatedAt(Instant.now());
        return step;
    }


    // toDto

    @Test
    void toDto_singlePendingStep_mapsAllFields() {
        Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");
        Pipeline pipeline = buildPipeline(1L, 10L, PipelineStatus.RUNNING, createdAt);
        Tool tool = buildTool(5L, "N4 Bias Field Correction");
        PipelineStep step = buildStep(100L, pipeline, 0, tool, PipelineStatus.RUNNING);

        PipelineDto dto = PipelineMapper.toDto(pipeline, List.of(step));

        assertEquals(1L,        dto.getId());
        assertEquals(10L,       dto.getImageId());
        assertEquals("RUNNING", dto.getStatus());
        assertEquals(createdAt.toString(), dto.getCreatedAt());
        assertEquals(1, dto.getSteps().size());

        var stepDto = dto.getSteps().get(0);
        assertEquals(100L,                       stepDto.getId());
        assertEquals(0,                          stepDto.getStepOrder());
        assertEquals(5L,                         stepDto.getToolId());
        assertEquals("N4 Bias Field Correction", stepDto.getToolName());
        assertEquals(10L,                        stepDto.getInputImageId()); // original image
        assertNull(stepDto.getAnalysisJobId());
        assertNull(stepDto.getOutputImageId());
        assertEquals("RUNNING",                  stepDto.getStatus());
    }

    @Test
    void toDto_nullCreatedAt_setsCreatedAtToNull() {
        Pipeline pipeline = buildPipeline(1L, 10L, PipelineStatus.PENDING, null);
        Tool tool = buildTool(5L, "N4");
        PipelineStep step = buildStep(100L, pipeline, 0, tool, PipelineStatus.PENDING);

        PipelineDto dto = PipelineMapper.toDto(pipeline, List.of(step));

        assertNull(dto.getCreatedAt());
    }

    @Test
    void toDto_twoSteps_threadsInputImageIdThroughPreviousStepOutput() {
        Pipeline pipeline = buildPipeline(1L, 10L, PipelineStatus.RUNNING, Instant.now());
        Tool tool1 = buildTool(5L, "N4 Bias Field Correction");
        Tool tool2 = buildTool(6L, "Otsu Threshold");

        PipelineStep step0 = buildStep(100L, pipeline, 0, tool1, PipelineStatus.COMPLETED);
        DicomImage outputImage = new DicomImage();
        outputImage.setId(20L);
        step0.setOutputImage(outputImage);

        PipelineStep step1 = buildStep(101L, pipeline, 1, tool2, PipelineStatus.PENDING);

        PipelineDto dto = PipelineMapper.toDto(pipeline, List.of(step0, step1));

        assertEquals(2, dto.getSteps().size());
        assertEquals(10L, dto.getSteps().get(0).getInputImageId()); // original image
        assertEquals(20L, dto.getSteps().get(1).getInputImageId()); // step0's output
    }

    @Test
    void toDto_emptySteps_returnsEmptyStepsList() {
        Pipeline pipeline = buildPipeline(1L, 10L, PipelineStatus.COMPLETED, Instant.now());

        PipelineDto dto = PipelineMapper.toDto(pipeline, List.of());

        assertNotNull(dto.getSteps());
        assertTrue(dto.getSteps().isEmpty());
    }

    @Test
    void toDto_stepWithAnalysisJobAndOutputImage_mapsJobIdAndOutputImageId() {
        Pipeline pipeline = buildPipeline(1L, 10L, PipelineStatus.COMPLETED, Instant.now());
        Tool tool = buildTool(5L, "N4");
        PipelineStep step = buildStep(100L, pipeline, 0, tool, PipelineStatus.COMPLETED);

        AnalysisJob job = new AnalysisJob();
        job.setId(55L);
        step.setAnalysisJob(job);

        DicomImage output = new DicomImage();
        output.setId(77L);
        step.setOutputImage(output);

        PipelineDto dto = PipelineMapper.toDto(pipeline, List.of(step));

        assertEquals(55L, dto.getSteps().get(0).getAnalysisJobId());
        assertEquals(77L, dto.getSteps().get(0).getOutputImageId());
    }

    @Test
    void toDto_mapsAllPipelineStatuses() {
        for (PipelineStatus status : PipelineStatus.values()) {
            Pipeline pipeline = buildPipeline(1L, 10L, status, null);
            Tool tool = buildTool(5L, "N4");
            PipelineStep step = buildStep(100L, pipeline, 0, tool, status);

            PipelineDto dto = PipelineMapper.toDto(pipeline, List.of(step));

            assertEquals(status.name(), dto.getStatus());
            assertEquals(status.name(), dto.getSteps().get(0).getStatus());
        }
    }
}
