package com.green.imagecore.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class RecentJobDto {
    private Long id;
    private String status;
    private Double durationSeconds;
    private Instant createdAt;
}
