package com.green.imagecore.service;

import com.green.imagecore.entities.Tool;
import com.green.imagecore.entities.subscription.SubscriptionTier;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.ToolRepository;
import com.green.imagecore.repositories.subscription.SubscriptionTierRepository;
import com.green.imagecore.repositories.subscription.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionAccessService {
    private final ToolRepository toolRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionTierRepository subscriptionTierRepository;

    @Transactional(readOnly = true)
    public boolean canUserAccessTool(Long userId, Long toolId) {
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found"));

        SubscriptionTier requiredTier = tool.getRequiredTier();

        SubscriptionTierCode userTierCode = userSubscriptionRepository.findByUserIdAndActiveTrue(userId)
                .map(sub -> sub.getTier().getCode())
                .orElse(SubscriptionTierCode.FREE);

        SubscriptionTier userTier = subscriptionTierRepository.findByCode(userTierCode)
                .orElseThrow(() -> new IllegalStateException("User tier not found"));

        return userTier.getSortOrder() >= requiredTier.getSortOrder();
    }
}
