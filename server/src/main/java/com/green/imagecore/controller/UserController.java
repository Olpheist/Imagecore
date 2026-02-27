package com.green.imagecore.controller;

import com.green.imagecore.dto.UserDto;
import com.green.imagecore.entities.User;
import com.green.imagecore.mapper.UserMapper;
import com.green.imagecore.service.UserService;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.findByUsernameWithRoles(username);

        return ResponseEntity.ok(UserMapper.toDto(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<UserDto> getAllUsers() {
        return userService.findAllWithRoles()
                .stream()
                .map(UserMapper::toDto)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/roles")
    public ResponseEntity<UserDto> updateRoles(@PathVariable Long id, @RequestBody UpdateRolesRequest request) {
        User user = userService.updateUserRoles(id, request.roleIds);

        return ResponseEntity.ok(UserMapper.toDto(user));
    }

    public record UpdateRolesRequest(
            List<Long> roleIds
    ) {}
}