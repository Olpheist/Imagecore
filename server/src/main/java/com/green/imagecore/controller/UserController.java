package com.green.imagecore.controller;

import com.green.imagecore.dto.UserDto;
import com.green.imagecore.entities.User;
import com.green.imagecore.mapper.UserMapper;
import com.green.imagecore.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.findByUsernameWithRoles(username);

        return ResponseEntity.ok(UserMapper.toDto(user));
    }
}