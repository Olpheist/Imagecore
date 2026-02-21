package com.green.imagecore.service;

import com.green.imagecore.entities.Role;
import com.green.imagecore.entities.User;
import com.green.imagecore.entities.UserRole;
import com.green.imagecore.entities.RoleType; // Using your existing enum
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserAuthenticationService userAuthenticationService;

    @Test
    void loadUserByUsername_Success() {
        // happy path
        String username = "doctor_smith";
        User mockUser = new User();
        mockUser.setUsername(username);
        mockUser.setPasswordHash("hashed_pw");
        mockUser.setEnabled(true);

        Role roleEntity = new Role();
        roleEntity.setName(RoleType.CLINICIAN);

        UserRole userRole = new UserRole();
        userRole.setRole(roleEntity);
        mockUser.setUserRoles(Set.of(userRole));

        when(userService.findByUsername(username)).thenReturn(mockUser);

        UserDetails userDetails = userAuthenticationService.loadUserByUsername(username);

        assertNotNull(userDetails);
        assertEquals(username, userDetails.getUsername());
        // Verify the ROLE_ prefix is correctly appended to your enum name
        assertTrue(userDetails.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CLINICIAN")));
    }

    @Test
    void loadUserByUsername_UserNotFound() {
        // sad path
        String username = "unknown_user";
        when(userService.findByUsername(username)).thenThrow(new UsernameNotFoundException("User not found"));

        assertThrows(UsernameNotFoundException.class, () ->
                userAuthenticationService.loadUserByUsername(username)
        );
    }
}