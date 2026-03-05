package com.green.imagecore.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class LogDto {
    Long id;
    Instant createdAt;
    String logLevel;
    String username;
    String message;
}
