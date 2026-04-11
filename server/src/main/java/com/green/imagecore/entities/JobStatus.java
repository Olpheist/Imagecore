package com.green.imagecore.entities;

public enum JobStatus {
    PENDING,     // job record created, ECS RunTask not yet called
    SUBMITTED,   // ECS accepted the RunTask call, container not yet started
    RUNNING,     // ECS container is actively executing the tool
    COMPLETED,   // tool finished successfully
    FAILED       // ECS call failed or tool exited with an error
}
