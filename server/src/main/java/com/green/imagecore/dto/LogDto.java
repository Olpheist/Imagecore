package com.green.imagecore.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
public class LogDto {
    private Long id;
    private Instant createdAt;
    private String logLevel;
    private String username;
    private String method;
    private String path;
    private Integer status;
    private Integer durationMs;
    private Map<String, String> queryParams;
}
