package com.green.imagecore.service;

import com.green.imagecore.entities.Role;
import com.green.imagecore.entities.RoleType;
import com.green.imagecore.entities.User;
import com.green.imagecore.entities.UserRole;
import com.green.imagecore.exception.ResourceNotFoundException;
import com.green.imagecore.repositories.RoleRepository;
import com.green.imagecore.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Mock
    private RoleRepository roleRepository;

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

        assertThrows(ResourceNotFoundException.class, () -> userService.findById(99L));
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

        assertThrows(ResourceNotFoundException.class, () -> userService.findByUsername("ghost"));
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

        assertThrows(ResourceNotFoundException.class, () -> userService.findByEmail("none@test.com"));
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

        assertThrows(ResourceNotFoundException.class, () -> userService.findByUsernameWithRoles("missing"));
    }

    // --- findAllWithRoles ---

    @Test
    void findAllWithRoles_ReturnsList() {
        User user1 = new User();
        user1.setId(1L);
        User user2 = new User();
        user2.setId(2L);

        when(userRepository.findAllWithRoles()).thenReturn(List.of(user1, user2));

        List<User> result = userService.findAllWithRoles();

        assertEquals(2, result.size());
        verify(userRepository).findAllWithRoles();
    }

    @Test
    void findAllWithRoles_ReturnsEmptyList_WhenNoUsers() {
        when(userRepository.findAllWithRoles()).thenReturn(List.of());

        List<User> result = userService.findAllWithRoles();

        assertTrue(result.isEmpty());
    }

    // --- updateUserRoles ---

    @Test
    void updateUserRoles_Success() {
        Role adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setName(RoleType.ADMIN);

        User user = new User();
        user.setId(1L);
        user.setUserRoles(new HashSet<>());

        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(List.of(1L))).thenReturn(List.of(adminRole));

        User result = userService.updateUserRoles(1L, List.of(1L));

        assertEquals(1, result.getUserRoles().size());
    }

    @Test
    void updateUserRoles_ThrowsException_WhenUserNotFound() {
        when(userRepository.findByIdWithRoles(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                userService.updateUserRoles(99L, List.of(1L))
        );
    }

    @Test
    void updateUserRoles_ThrowsException_WhenInvalidRoleId() {
        User user = new User();
        user.setId(1L);
        user.setUserRoles(new HashSet<>());

        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(List.of(99L))).thenReturn(List.of()); // role not found

        assertThrows(IllegalArgumentException.class, () ->
                userService.updateUserRoles(1L, List.of(99L))
        );
    }

    @Test
    void updateUserRoles_NullRoleIds_ClearsAllRoles() {
        Role existingRole = new Role();
        existingRole.setId(1L);
        existingRole.setName(RoleType.ADMIN);

        User user = new User();
        user.setId(1L);
        user.setUserRoles(new HashSet<>(Set.of(new UserRole(user, existingRole))));

        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(List.of())).thenReturn(List.of());

        User result = userService.updateUserRoles(1L, null);

        assertTrue(result.getUserRoles().isEmpty());
    }

    @Test
    void updateUserRoles_RemovesOldRoles_AddsNewOnes() {
        Role oldRole = new Role();
        oldRole.setId(1L);
        oldRole.setName(RoleType.CLINICIAN);

        Role newRole = new Role();
        newRole.setId(2L);
        newRole.setName(RoleType.ADMIN);

        User user = new User();
        user.setId(1L);
        user.setUserRoles(new HashSet<>(Set.of(new UserRole(user, oldRole))));

        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(List.of(2L))).thenReturn(List.of(newRole));

        User result = userService.updateUserRoles(1L, List.of(2L));

        Set<Long> resultRoleIds = result.getUserRoles().stream()
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toSet());

        assertFalse(resultRoleIds.contains(1L)); // old role removed
        assertTrue(resultRoleIds.contains(2L));  // new role added
    }

    @Test
    void deleteUser_Success() {
        Long userId = 1L;

        when(userRepository.existsById(userId)).thenReturn(true);
        doNothing().when(userRepository).deleteById(userId);

        assertDoesNotThrow(() -> userService.deleteUser(userId));

        verify(userRepository).existsById(userId);
        verify(userRepository).deleteById(userId);
    }

    @Test
    void deleteUser_ThrowsException_WhenUserNotFound() {
        Long userId = 99L;

        when(userRepository.existsById(userId)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.deleteUser(userId)
        );

        assertEquals("User not found with id: 99", exception.getMessage());
        verify(userRepository).existsById(userId);
        verify(userRepository, never()).deleteById(anyLong());
    }
}