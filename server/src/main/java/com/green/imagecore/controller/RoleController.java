package com.green.imagecore.controller;

import com.green.imagecore.dto.RoleDto;
import com.green.imagecore.entities.Role;
import com.green.imagecore.mapper.RoleMapper;
import com.green.imagecore.service.RoleService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/roles")
public class RoleController {
    private final RoleService roleService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<RoleDto>> roles() {
        return ResponseEntity.ok(RoleMapper.toDtos(roleService.findAll()));
    }
}
