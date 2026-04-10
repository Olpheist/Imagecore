package com.green.imagecore.repositories.subscription;

import com.green.imagecore.entities.subscription.BillingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingEventRepository extends JpaRepository<BillingEvent, Long> {
    boolean existsByProviderEventId(String providerEventId);
}
