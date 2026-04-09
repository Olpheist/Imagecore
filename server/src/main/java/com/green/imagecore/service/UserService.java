package com.green.imagecore.service;

import com.green.imagecore.entities.Role;
import com.green.imagecore.entities.User;
import com.green.imagecore.entities.UserRole;
import com.green.imagecore.entities.subscription.SubscriptionTier;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.entities.subscription.UserSubscription;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.RoleRepository;
import com.green.imagecore.repositories.UserRepository;
import com.green.imagecore.repositories.subscription.SubscriptionTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionTierRepository subscriptionTierRepository;

    @Transactional
    public User register(String email, String username, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already in use");
        }

        SubscriptionTier freeTier = subscriptionTierRepository.findByCode(SubscriptionTierCode.FREE)
                .orElseThrow(() -> new IllegalStateException("FREE subscription tier not found"));

        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);

        UserSubscription userSubscription = new UserSubscription();
        userSubscription.setUser(user);
        userSubscription.setTier(freeTier);
        userSubscription.setAutoRenew(false);

        user.setUserSubscription(userSubscription);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    @Transactional(readOnly = true)
    public User findByUsernameWithRoles(String username) {
        return userRepository.findByUsernameWithRoles(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public List<User> findAllWithRoles() {
        return userRepository.findAllWithRoles();
    }

    @Transactional
    public User updateUserRoles(Long userId, List<Long> roleIds) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Long> requested = (roleIds == null) ? List.of() : roleIds;
        List<Role> roles = roleRepository.findAllById(requested);
        if (roles.size() != requested.size()) {
            throw new IllegalArgumentException("Invalid roleIds");
        }

        Set<Long> requestedIds = new HashSet<>(requested);

        // remove only the ones not requested anymore
        user.getUserRoles().removeIf(ur -> !requestedIds.contains(ur.getRole().getId()));

        // add missing ones
        Set<Long> currentIds = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toSet());

        for (Role role : roles) {
            if (!currentIds.contains(role.getId())) {
                user.getUserRoles().add(new UserRole(user, role));
            }
        }

        return user;
    }

    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        userRepository.deleteById(userId);
    }
}
