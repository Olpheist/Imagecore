package com.green.imagecore.entities;

import com.green.imagecore.entities.subscription.SubscriptionTier;
import jakarta.persistence.*;
import lombok.*;

/**
 * Definition for the tool entity
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tools")
public class Tool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long toolId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "image_tag", length = 80)
    private String imageTag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_tier_code", referencedColumnName = "code", nullable = false)
    private SubscriptionTier requiredTier;
}
