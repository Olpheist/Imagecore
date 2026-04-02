package com.green.imagecore.repositories.subscription;

import com.green.imagecore.entities.subscription.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    Optional<UserSubscription> findByUserIdAndActiveTrue(Long userId);
    Optional<UserSubscription> findByProviderSubscriptionId(String providerSubscriptionId);
}
