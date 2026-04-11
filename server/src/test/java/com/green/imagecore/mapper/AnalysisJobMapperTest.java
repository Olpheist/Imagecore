package com.green.imagecore.mapper;

import com.green.imagecore.dto.AnalysisJobDto;
import com.green.imagecore.entities.AnalysisJob;
import com.green.imagecore.entities.DicomImage;
import com.green.imagecore.entities.JobStatus;
import com.green.imagecore.entities.Tool;
import com.green.imagecore.entities.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisJobMapperTest {

    // helpers

    private AnalysisJob buildJob(Long id, JobStatus status, Instant createdAt, String ecsTaskArn) {
        User user = new User();
        user.setId(1L);

        DicomImage image = new DicomImage();
        image.setId(10L);

        Tool tool = new Tool();
        tool.setToolId(5L);
        tool.setName("N4 Bias Field Correction");

        return AnalysisJob.builder()
                .id(id)
                .image(image)
                .tool(tool)
                .user(user)
                .status(status)
                .ecsTaskArn(ecsTaskArn)
                .updatedAt(Instant.now())
                .build();
    }


    // toDto

    @Test
    void toDto_nullJob_returnsNull() {
        assertNull(AnalysisJobMapper.toDto(null));
    }

    @Test
    void toDto_validJob_mapsAllFieldsCorrectly() {
        Instant createdAt = Instant.parse("2026-04-11T10:00:00Z");
        AnalysisJob job = buildJob(42L, JobStatus.SUBMITTED, createdAt, "arn:aws:ecs:us-east-1:123:task/cluster/task-001");
        job.setCreatedAt(createdAt);

        AnalysisJobDto dto = AnalysisJobMapper.toDto(job);

        assertNotNull(dto);
        assertEquals(42L,                                               dto.getId());
        assertEquals(10L,                                               dto.getImageId());
        assertEquals(5L,                                                dto.getToolId());
        assertEquals("N4 Bias Field Correction",                        dto.getToolName());
        assertEquals("SUBMITTED",                                       dto.getStatus());
        assertEquals("arn:aws:ecs:us-east-1:123:task/cluster/task-001", dto.getEcsTaskArn());
        assertEquals(createdAt.toString(),                              dto.getCreatedAt());
    }

    @Test
    void toDto_nullCreatedAt_setsCreatedAtToNull() {
        AnalysisJob job = buildJob(1L, JobStatus.PENDING, null, null);

        AnalysisJobDto dto = AnalysisJobMapper.toDto(job);

        assertNotNull(dto);
        assertNull(dto.getCreatedAt());
    }

    @Test
    void toDto_nullEcsTaskArn_setsEcsTaskArnToNull() {
        AnalysisJob job = buildJob(1L, JobStatus.PENDING, null, null);

        AnalysisJobDto dto = AnalysisJobMapper.toDto(job);

        assertNull(dto.getEcsTaskArn());
    }

    @Test
    void toDto_mapsAllJobStatuses() {
        for (JobStatus status : JobStatus.values()) {
            AnalysisJob job = buildJob(1L, status, null, null);

            AnalysisJobDto dto = AnalysisJobMapper.toDto(job);

            assertEquals(status.name(), dto.getStatus());
        }
    }

    @Test
    void toDto_doesNotMutateOriginalJob() {
        Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");
        AnalysisJob job = buildJob(7L, JobStatus.RUNNING, createdAt, "arn:task-abc");
        job.setCreatedAt(createdAt);

        AnalysisJobMapper.toDto(job);

        assertEquals(7L,              job.getId());
        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertEquals("arn:task-abc",  job.getEcsTaskArn());
    }


    // toDtos

    @Test
    void toDtos_nullList_returnsEmptyList() {
        assertTrue(AnalysisJobMapper.toDtos(null).isEmpty());
    }

    @Test
    void toDtos_emptyList_returnsEmptyList() {
        assertTrue(AnalysisJobMapper.toDtos(List.of()).isEmpty());
    }

    @Test
    void toDtos_validList_mapsAllJobs() {
        AnalysisJob job1 = buildJob(1L, JobStatus.SUBMITTED, null, "arn:task-1");
        AnalysisJob job2 = buildJob(2L, JobStatus.COMPLETED, null, "arn:task-2");

        List<AnalysisJobDto> dtos = AnalysisJobMapper.toDtos(List.of(job1, job2));

        assertEquals(2, dtos.size());
        assertEquals(1L,          dtos.get(0).getId());
        assertEquals("SUBMITTED", dtos.get(0).getStatus());
        assertEquals(2L,          dtos.get(1).getId());
        assertEquals("COMPLETED", dtos.get(1).getStatus());
    }

    @Test
    void toDtos_preservesOrderOfInputList() {
        AnalysisJob job1 = buildJob(1L, JobStatus.PENDING,   null, null);
        AnalysisJob job2 = buildJob(2L, JobStatus.RUNNING,   null, null);
        AnalysisJob job3 = buildJob(3L, JobStatus.COMPLETED, null, null);

        List<AnalysisJobDto> dtos = AnalysisJobMapper.toDtos(List.of(job1, job2, job3));

        assertEquals("PENDING",   dtos.get(0).getStatus());
        assertEquals("RUNNING",   dtos.get(1).getStatus());
        assertEquals("COMPLETED", dtos.get(2).getStatus());
    }

    @Test
    void toDtos_listContainingNullJob_includesNullInResult() {
        AnalysisJob job = buildJob(1L, JobStatus.SUBMITTED, null, null);
        List<AnalysisJob> jobs = new ArrayList<>();
        jobs.add(job);
        jobs.add(null);

        List<AnalysisJobDto> dtos = AnalysisJobMapper.toDtos(jobs);

        assertEquals(2, dtos.size());
        assertNotNull(dtos.get(0));
        assertNull(dtos.get(1));
    }

    @Test
    void toDtos_singleItemList_returnsOneDto() {
        AnalysisJob job = buildJob(99L, JobStatus.FAILED, null, null);

        List<AnalysisJobDto> dtos = AnalysisJobMapper.toDtos(List.of(job));

        assertEquals(1, dtos.size());
        assertEquals(99L,     dtos.get(0).getId());
        assertEquals("FAILED", dtos.get(0).getStatus());
    }
}
