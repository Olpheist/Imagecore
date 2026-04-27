package com.green.imagecore.service;

import com.green.imagecore.entities.Role;
import com.green.imagecore.entities.RoleType;
import com.green.imagecore.entities.User;
import com.green.imagecore.entities.subscription.SubscriptionTier;
import com.green.imagecore.entities.subscription.SubscriptionTierCode;
import com.green.imagecore.repositories.RoleRepository;
import com.green.imagecore.repositories.UserRepository;
import com.green.imagecore.repositories.subscription.SubscriptionTierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private GoogleTokenVerifier tokenVerifier;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private SubscriptionTierRepository subscriptionTierRepository;

    @InjectMocks
    private GoogleAuthService googleAuthService;

    private static final String FAKE_TOKEN = "fake.google.token";
    private static final GoogleTokenInfo TOKEN_INFO =
            new GoogleTokenInfo("google-sub-123", "user@gmail.com", "Test User");

    @Test
    void authenticateWithGoogle_NewUser_CreatesAndReturnsUser() {
        SubscriptionTier freeTier = new SubscriptionTier();
        freeTier.setId(1L);
        freeTier.setCode(SubscriptionTierCode.FREE);

        Role patientRole = new Role();
        patientRole.setId(1L);
        patientRole.setName(RoleType.PATIENT);

        when(tokenVerifier.verify(FAKE_TOKEN)).thenReturn(TOKEN_INFO);
        when(userRepository.findByGoogleId(TOKEN_INFO.sub())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(TOKEN_INFO.email())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("user")).thenReturn(false);
        when(subscriptionTierRepository.findByCode(SubscriptionTierCode.FREE)).thenReturn(Optional.of(freeTier));
        when(roleRepository.findByName(RoleType.PATIENT)).thenReturn(Optional.of(patientRole));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        User result = googleAuthService.authenticateWithGoogle(FAKE_TOKEN);

        assertNotNull(result);
        assertEquals("user@gmail.com", result.getEmail());
        assertEquals("google-sub-123", result.getGoogleId());
        assertNull(result.getPasswordHash());
        assertEquals(1, result.getUserRoles().size());
        assertEquals(RoleType.PATIENT, result.getUserRoles().iterator().next().getRole().getName());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void authenticateWithGoogle_ExistingGoogleUser_ReturnsExistingUser() {
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setGoogleId("google-sub-123");
        existingUser.setEmail("user@gmail.com");

        when(tokenVerifier.verify(FAKE_TOKEN)).thenReturn(TOKEN_INFO);
        when(userRepository.findByGoogleId(TOKEN_INFO.sub())).thenReturn(Optional.of(existingUser));

        User result = googleAuthService.authenticateWithGoogle(FAKE_TOKEN);

        assertEquals(1L, result.getId());
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void authenticateWithGoogle_ExistingEmailUser_LinksGoogleIdAndReturnsUser() {
        User existingUser = new User();
        existingUser.setId(2L);
        existingUser.setEmail("user@gmail.com");
        existingUser.setGoogleId(null);

        when(tokenVerifier.verify(FAKE_TOKEN)).thenReturn(TOKEN_INFO);
        when(userRepository.findByGoogleId(TOKEN_INFO.sub())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(TOKEN_INFO.email())).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User result = googleAuthService.authenticateWithGoogle(FAKE_TOKEN);

        assertEquals("google-sub-123", result.getGoogleId());
        verify(userRepository).save(existingUser);
    }

    @Test
    void authenticateWithGoogle_InvalidToken_ThrowsAndSkipsUserLookup() {
        when(tokenVerifier.verify(FAKE_TOKEN))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid Google token"));

        assertThrows(ResponseStatusException.class,
                () -> googleAuthService.authenticateWithGoogle(FAKE_TOKEN));

        verify(userRepository, never()).findByGoogleId(any());
    }

    @Test
    void authenticateWithGoogle_NewUser_UsernameCollision_AppendsNumber() {
        SubscriptionTier freeTier = new SubscriptionTier();
        freeTier.setId(1L);
        freeTier.setCode(SubscriptionTierCode.FREE);

        Role patientRole = new Role();
        patientRole.setId(1L);
        patientRole.setName(RoleType.PATIENT);

        when(tokenVerifier.verify(FAKE_TOKEN)).thenReturn(TOKEN_INFO);
        when(userRepository.findByGoogleId(TOKEN_INFO.sub())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(TOKEN_INFO.email())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("user")).thenReturn(true);
        when(userRepository.existsByUsername("user2")).thenReturn(false);
        when(subscriptionTierRepository.findByCode(SubscriptionTierCode.FREE)).thenReturn(Optional.of(freeTier));
        when(roleRepository.findByName(RoleType.PATIENT)).thenReturn(Optional.of(patientRole));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        User result = googleAuthService.authenticateWithGoogle(FAKE_TOKEN);

        assertEquals("user2", result.getUsername());
    }

    @Test
    void authenticateWithGoogle_NewUser_PatientRoleNotFound_Throws() {
        SubscriptionTier freeTier = new SubscriptionTier();
        freeTier.setId(1L);
        freeTier.setCode(SubscriptionTierCode.FREE);

        when(tokenVerifier.verify(FAKE_TOKEN)).thenReturn(TOKEN_INFO);
        when(userRepository.findByGoogleId(TOKEN_INFO.sub())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(TOKEN_INFO.email())).thenReturn(Optional.empty());
        when(subscriptionTierRepository.findByCode(SubscriptionTierCode.FREE)).thenReturn(Optional.of(freeTier));
        when(roleRepository.findByName(RoleType.PATIENT)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> googleAuthService.authenticateWithGoogle(FAKE_TOKEN));

        verify(userRepository, never()).save(any());
    }
}
