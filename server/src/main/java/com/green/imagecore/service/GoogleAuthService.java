package com.green.imagecore.service;

import com.green.imagecore.entities.User;
import com.green.imagecore.entities.subscription.SubscriptionTier;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.entities.subscription.UserSubscription;
import com.green.imagecore.repositories.UserRepository;
import com.green.imagecore.repositories.subscription.SubscriptionTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleTokenVerifier tokenVerifier;
    private final UserRepository userRepository;
    private final SubscriptionTierRepository subscriptionTierRepository;

    @Transactional
    public User authenticateWithGoogle(String idToken) {
        GoogleTokenInfo tokenInfo = tokenVerifier.verify(idToken);

        Optional<User> byGoogleId = userRepository.findByGoogleId(tokenInfo.sub());
        if (byGoogleId.isPresent()) {
            return byGoogleId.get();
        }

        Optional<User> byEmail = userRepository.findByEmail(tokenInfo.email());
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            user.setGoogleId(tokenInfo.sub());
            return userRepository.save(user);
        }

        return createUser(tokenInfo);
    }

    private User createUser(GoogleTokenInfo tokenInfo) {
        SubscriptionTier freeTier = subscriptionTierRepository.findByCode(SubscriptionTierCode.FREE)
                .orElseThrow(() -> new IllegalStateException("FREE subscription tier not found"));

        User user = new User();
        user.setEmail(tokenInfo.email().toLowerCase());
        user.setUsername(deriveUsername(tokenInfo.email()));
        user.setGoogleId(tokenInfo.sub());
        user.setEnabled(true);

        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setTier(freeTier);
        subscription.setAutoRenew(false);
        user.setUserSubscription(subscription);

        return userRepository.save(user);
    }

    private String deriveUsername(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_");
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        int suffix = 2;
        while (userRepository.existsByUsername(base + suffix)) {
            suffix++;
        }
        return base + suffix;
    }
}
