package com.green.imagecore.repositories.subscription;

import com.green.imagecore.entities.subscription.SubscriptionTier;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionTierRepository extends JpaRepository<SubscriptionTier, Long> {
    Optional<SubscriptionTier> findByCode(SubscriptionTierCode code);
}
