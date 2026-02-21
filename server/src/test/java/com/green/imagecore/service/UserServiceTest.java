package com.green.imagecore.service;

import com.green.imagecore.entities.User;
import com.green.imagecore.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void register_Success() {
        String email = "doctor@imagecore.com";
        String username = "dr_smith";
        String password = "securePassword";

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("hashed_pw");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        User result = userService.register(email, username, password);

        assertNotNull(result);
        assertEquals(email.toLowerCase(), result.getEmail());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_ThrowsException_WhenEmailAlreadyInUse() {
        String email = "duplicate@test.com";
        when(userRepository.existsByEmail(email)).thenReturn(true);

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                userService.register(email, "user", "pass")
        );

        assertEquals("Email already in use", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_ThrowsException_WhenUsernameAlreadyInUse() {
        String username = "existing_user";
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(username)).thenReturn(true);

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                userService.register("new@test.com", username, "pass")
        );

        assertEquals("Username already in use", exception.getMessage());
    }

    // --- LOOKUP TESTS ---

    @Test
    void findById_Success() {
        Long id = 1L;
        User user = new User();
        user.setId(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        User result = userService.findById(id);
        assertEquals(id, result.getId());
    }

    @Test
    void findById_ThrowsException_WhenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.findById(99L));
    }

    @Test
    void findByUsername_Success() {
        String username = "admin";
        User user = new User();
        user.setUsername(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        User result = userService.findByUsername(username);
        assertEquals(username, result.getUsername());
    }

    @Test
    void findByUsername_ThrowsException_WhenNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userService.findByUsername("ghost"));
    }

    @Test
    void findByEmail_Success() {
        String email = "test@test.com";
        User user = new User();
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        User result = userService.findByEmail(email);
        assertEquals(email, result.getEmail());
    }

    @Test
    void findByEmail_ThrowsException_WhenNotFound() {
        when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.findByEmail("none@test.com"));
    }

    @Test
    void findByUsernameWithRoles_Success() {
        String username = "clinician_1";
        User user = new User();
        user.setUsername(username);
        when(userRepository.findByUsernameWithRoles(username)).thenReturn(Optional.of(user));

        User result = userService.findByUsernameWithRoles(username);
        assertEquals(username, result.getUsername());
    }

    @Test
    void findByUsernameWithRoles_ThrowsException_WhenNotFound() {
        when(userRepository.findByUsernameWithRoles("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.findByUsernameWithRoles("missing"));
    }
}