package com.green.imagecore.service;

import com.green.imagecore.entities.PasswordReset;
import com.green.imagecore.repositories.PasswordResetRepository;
import com.green.imagecore.repositories.UserRepository;
import com.green.imagecore.entities.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetRepository passwordResetRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    @Value("${app.base-url}")
    private String baseUrl;

    private static final SecureRandom RNG = new SecureRandom();
    private static final long TTL_MS = 30L * 60L * 1000L; // 30 minutes

    @Transactional
    public void requestReset(String email) {
        // clean up expired tokens
        passwordResetRepository.deleteByExpiresAtBefore(new Date());

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }

        passwordResetRepository.deleteByUserId(user.getId());

        String rawToken = generateToken();
        String tokenHash = sha256Base64(rawToken);

        PasswordReset prt = new PasswordReset();
        prt.setUserId(user.getId());
        prt.setTokenHash(tokenHash);
        prt.setCreatedAt(new Date());
        prt.setExpiresAt(new Date(System.currentTimeMillis() + TTL_MS));
        prt.setUsedAt(null);

        passwordResetRepository.save(prt);

        String link = baseUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), link);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = sha256Base64(rawToken);

        PasswordReset prt = passwordResetRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token."));

        if (prt.getUsedAt() != null) {
            throw new IllegalArgumentException("Invalid or expired reset token.");
        }

        if (prt.getExpiresAt().before(new Date())) {
            throw new IllegalArgumentException("Invalid or expired reset token.");
        }

        User user = userRepository.findById(prt.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        prt.setUsedAt(new Date());
        passwordResetRepository.save(prt);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Base64(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token.", e);
        }
    }
}