package com.green.imagecore.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class UserDto {
    private Long id;
    private String email;
    private String username;
    private boolean enabled;
    private Instant createdAt;
    private Set<UserRoleDto> userRoles = new HashSet<>();
}
