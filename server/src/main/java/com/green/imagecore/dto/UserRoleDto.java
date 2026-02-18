package com.green.imagecore.dto;

import com.green.imagecore.entities.UserRole;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class UserRoleDto {
    private Long roleId;
    private String roleName;
}
