package com.green.imagecore.service;

import com.green.imagecore.entities.PasswordReset;
import com.green.imagecore.entities.User;
import com.green.imagecore.repositories.PasswordResetRepository;
import com.green.imagecore.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private PasswordResetRepository passwordResetRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(passwordResetRepository, userRepository, passwordEncoder, emailService);
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:3000");
    }

    @Test
    void requestReset_UserNotFound_DoesNothing() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        service.requestReset("missing@test.com");

        verify(passwordResetRepository).deleteByExpiresAtBefore(any(Instant.class));
        verify(passwordResetRepository, never()).deleteByUserId(anyLong());
        verify(passwordResetRepository, never()).save(any(PasswordReset.class));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void requestReset_UserFound_SavesTokenAndSendsEmail() {
        User u = new User();
        u.setId(7L);
        u.setEmail("user@test.com");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(u));

        service.requestReset("user@test.com");

        verify(passwordResetRepository).deleteByExpiresAtBefore(any(Instant.class));
        verify(passwordResetRepository).deleteByUserId(7L);

        ArgumentCaptor<PasswordReset> prtCaptor = ArgumentCaptor.forClass(PasswordReset.class);
        verify(passwordResetRepository).save(prtCaptor.capture());

        PasswordReset saved = prtCaptor.getValue();
        assertEquals(7L, saved.getUserId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getExpiresAt());
        assertNull(saved.getUsedAt());
        assertNotNull(saved.getTokenHash());
        assertFalse(saved.getTokenHash().isBlank());

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(emailCaptor.capture(), linkCaptor.capture());

        assertEquals("user@test.com", emailCaptor.getValue());
        assertTrue(linkCaptor.getValue().startsWith("http://localhost:3000/reset-password?token="));
    }

    @Test
    void resetPassword_TokenNotFound_Throws() {
        when(passwordResetRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("raw-token", "password10"));

        verify(userRepository, never()).save(any(User.class));
        verify(passwordResetRepository, never()).save(any(PasswordReset.class));
    }

    @Test
    void resetPassword_TokenAlreadyUsed_Throws() {
        PasswordReset prt = new PasswordReset();
        prt.setUserId(1L);
        prt.setExpiresAt(Instant.now().plusMillis(60000));
        prt.setUsedAt(Instant.now()); // already used

        when(passwordResetRepository.findByTokenHash(anyString())).thenReturn(Optional.of(prt));

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("raw-token", "password10"));

        verify(userRepository, never()).save(any(User.class));
        verify(passwordResetRepository, never()).save(any(PasswordReset.class));
    }

    @Test
    void resetPassword_TokenExpired_Throws() {
        PasswordReset prt = new PasswordReset();
        prt.setUserId(1L);
        prt.setExpiresAt(Instant.now().plusMillis(-60000)); // expired
        prt.setUsedAt(null);

        when(passwordResetRepository.findByTokenHash(anyString())).thenReturn(Optional.of(prt));

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("raw-token", "password10"));

        verify(userRepository, never()).save(any(User.class));
        verify(passwordResetRepository, never()).save(any(PasswordReset.class));
    }

    @Test
    void resetPassword_UserMissing_Throws() {
        PasswordReset prt = new PasswordReset();
        prt.setUserId(999L);
        prt.setExpiresAt(Instant.now().plusMillis(60000));
        prt.setUsedAt(null);

        when(passwordResetRepository.findByTokenHash(anyString())).thenReturn(Optional.of(prt));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("raw-token", "password10"));

        verify(userRepository, never()).save(any(User.class));
        verify(passwordResetRepository, never()).save(any(PasswordReset.class));
    }

    @Test
    void resetPassword_HappyPath_UpdatesPasswordAndMarksTokenUsed() {
        PasswordReset prt = new PasswordReset();
        prt.setUserId(5L);
        prt.setExpiresAt(Instant.now().plusMillis(60000));
        prt.setUsedAt(null);

        User user = new User();
        user.setId(5L);

        when(passwordResetRepository.findByTokenHash(anyString())).thenReturn(Optional.of(prt));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("password10")).thenReturn("ENCODED");

        service.resetPassword("raw-token", "password10");

        verify(userRepository).save(argThat(saved -> "ENCODED".equals(saved.getPasswordHash())));

        ArgumentCaptor<PasswordReset> prtCaptor = ArgumentCaptor.forClass(PasswordReset.class);
        verify(passwordResetRepository).save(prtCaptor.capture());
        assertNotNull(prtCaptor.getValue().getUsedAt());
    }
}