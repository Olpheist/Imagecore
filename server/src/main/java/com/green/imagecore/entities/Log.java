package com.green.imagecore.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "audit_logs")
public class Log {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "log_level", nullable = false, length = 10)
    private String logLevel;

    @Column(name = "username")
    private String username;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "path", columnDefinition = "TEXT")
    private String path;

    @Column(name = "status")
    private Integer status;

    @Column(name = "duration_ms")
    private Integer durationMs;
}