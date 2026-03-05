package com.green.imagecore.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_logs")
public class Log {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    @Column(name = "log_level", nullable = false, length = 10)
    private String logLevel;

    @Column(name = "username")
    private String username;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;
}
