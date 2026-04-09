package com.green.imagecore.mapper;

import com.green.imagecore.dto.UserDto;
import com.green.imagecore.dto.UserRoleDto;
import com.green.imagecore.dto.UserSubscriptionDto;
import com.green.imagecore.entities.User;
import com.green.imagecore.entities.UserRole;
import com.green.imagecore.entities.subscription.UserSubscription;

import java.util.Set;
import java.util.stream.Collectors;

public class UserMapper {

    public static UserDto toDto(User user) {
        if (user == null) return null;

        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setUsername(user.getUsername());
        dto.setEnabled(user.isEnabled());
        dto.setCreatedAt(user.getCreatedAt());

        dto.setUserRoles(toRoleDtos(user.getUserRoles()));

        dto.setSubscription(toSubscriptionDto(user.getUserSubscription()));

        return dto;
    }

    private static Set<UserRoleDto> toRoleDtos(Set<UserRole> userRoles) {
        if (userRoles == null) return Set.of();

        return userRoles.stream()
                .map(UserMapper::toDto)
                .collect(Collectors.toSet());
    }

    private static UserRoleDto toDto(UserRole ur) {
        UserRoleDto d = new UserRoleDto();
        d.setRoleId(ur.getRole().getId());
        d.setRoleName(ur.getRole().getName().name());
        return d;
    }

    private static UserSubscriptionDto toSubscriptionDto(UserSubscription sub) {
        UserSubscriptionDto dto = new UserSubscriptionDto();

        dto.setTierCode(sub.getTier().getCode().name());
        dto.setAutoRenew(sub.isAutoRenew());
        dto.setCurrentPeriodStart(sub.getCurrentPeriodStart());
        dto.setCurrentPeriodEnd(sub.getCurrentPeriodEnd());

        return dto;
    }
}
