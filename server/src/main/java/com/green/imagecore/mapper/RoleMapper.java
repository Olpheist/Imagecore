package com.green.imagecore.mapper;

import com.green.imagecore.dto.RoleDto;
import com.green.imagecore.entities.Role;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RoleMapper {

    private RoleMapper() {}

    public static RoleDto toDto(Role role) {
        if (role == null) return null;

        RoleDto dto = new RoleDto();
        dto.setId(role.getId());
        dto.setName(role.getName().name());

        return dto;
    }

    public static List<RoleDto> toDtos(List<Role> roles) {
        if (roles == null) return Collections.emptyList();

        return roles.stream()
                .map(RoleMapper::toDto)
                .collect(Collectors.toList());
    }

    public static Role toEntity(RoleDto dto) {
        if (dto == null) return null;

        Role role = new Role();
        role.setId(dto.getId());
        role.setName(Enum.valueOf(
                com.green.imagecore.entities.RoleType.class,
                dto.getName()
        ));

        return role;
    }
}