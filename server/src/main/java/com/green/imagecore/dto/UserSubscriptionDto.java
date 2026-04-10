package com.green.imagecore.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class UserSubscriptionDto {
    private String tierCode;
    private boolean autoRenew;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
}
