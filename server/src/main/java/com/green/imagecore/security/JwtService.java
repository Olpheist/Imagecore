package com.green.imagecore.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for managing the lifecycle of JSON Web Tokens (JWT).
 * This service handles the creation and signing of stateless authentication tokens
 * used for secure communication between the Nuxt frontend and Spring Boot backend.
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.access-ttl-minutes}")
    private int jwtExpirationInMinutes;

    @Value("${app.jwt.issuer}")
    private String issuer;

    /**
     * Entry point for generating an access token for an authenticated user.
     * This method translates Spring Security's UserDetails into a structured JWT payload.
     *
     * @param userDetails The Spring Security principal containing the username and granted authorities.
     * @param userId The unique database primary key of the user.
     * @return A signed, Base64-encoded JWT string.
     */
    public String generateToken(UserDetails userDetails, String userId) {
        Map<String, Object> claims = new HashMap<>();

        // Embed the unique database ID as a custom claim for easy identification in downstream services
        claims.put("uid", userId);

        // Map the user's granted authorities (roles) into a simple string list
        // This allows the Resource Server to enforce Role-Based Access Control (RBAC) without a DB lookup
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        claims.put("roles", roles);

        // Build the final token using the user's email/username as the unique subject identifier
        return buildToken(claims, userDetails.getUsername());
    }

    /**
     * Assembles the JWT header, payload, and signature into a final compact string.
     * Uses HMAC-SHA256 for cryptographic signing to ensure data integrity and authenticity.
     *
     * @param extraClaims Map containing custom user-specific data (ID, roles).
     * @param subject The unique identifier (email) for whom the token is issued.
     * @return A complete, signed JWT.
     */
    private String buildToken(Map<String, Object> extraClaims, String subject) {
        // Calculate the timestamp when the token will become invalid
        long expirationTime = System.currentTimeMillis() + (long) jwtExpirationInMinutes * 60 * 1000;

        // Convert the configured secret string into a cryptographic key suitable for HMAC-SHA algorithms
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .setClaims(extraClaims)
                // Set standard reserved claims to provide context and prevent replay attacks
                .setSubject(subject)                               // Identity (sub)
                .setIssuer(issuer)                                 // Source of truth (iss)
                .setIssuedAt(new Date(System.currentTimeMillis())) // Creation time (iat)
                .setExpiration(new Date(expirationTime))           // Expiration time (exp)

                // Digitally sign the token. If the payload is modified, the signature check will fail.
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}