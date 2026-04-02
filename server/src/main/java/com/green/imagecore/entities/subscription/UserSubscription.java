package com.green.imagecore.entities.subscription;

import com.green.imagecore.entities.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_subscriptions")
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // tier
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id", nullable = false)
    private SubscriptionTier tier;

    // status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus status;

    @Column(name = "provider_customer_id", length = 255)
    private String providerCustomerId;

    @Column(name = "provider_subscription_id", length = 255)
    private String providerSubscriptionId;

    @Column(name = "provider_price_id", length = 255)
    private String providerPriceId;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at")
    private Instant cancelAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}