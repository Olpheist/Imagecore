package com.green.imagecore.service;

import com.green.imagecore.entities.Role;
import com.green.imagecore.entities.User;
import com.green.imagecore.entities.UserRole;
import com.green.imagecore.entities.RoleType;
import com.green.imagecore.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserAuthenticationService userAuthenticationService;

    @Test
    void loadUserByUsername_Success() {
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

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));

        UserDetails userDetails = userAuthenticationService.loadUserByUsername(username);

        assertNotNull(userDetails);
        assertEquals(username, userDetails.getUsername());
        assertTrue(userDetails.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CLINICIAN")));
    }

    @Test
    void loadUserByUsername_UserNotFound() {
        String username = "unknown_user";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () ->
                userAuthenticationService.loadUserByUsername(username)
        );
    }
}