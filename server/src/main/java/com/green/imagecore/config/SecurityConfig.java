package com.green.imagecore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import javax.crypto.spec.SecretKeySpec;
import java.util.List;

/**
 * Main security configuration for ImageCore.
 * This class defines the security filter chain, transitioning the application
 * from session-based security to a stateless JWT-based Resource Server model.
 */
@Configuration
@EnableMethodSecurity // Enables fine-grained access control with @PreAuthorize
public class SecurityConfig {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    /**
     * Configures the security filter chain.
     * Defines which endpoints are public, enforces statelessness, and configures
     * the application as an OAuth2 Resource Server to handle Bearer tokens.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RequestLogFilter requestLogFilter) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/stripe/webhook")
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )

                // Enforce statelessness by preventing the creation of HTTP sessions.
                // This is critical for horizontal scaling on cloud infrastructure.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/stripe/webhook").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )

                // Configure the app to treat incoming requests as Bearer tokens (JWT).
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )

                // add request log filter after auth to get user info
                .addFilterAfter(requestLogFilter, org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
                .build();
    }

    /**
     * Defines the component responsible for verifying the cryptographic signature of JWTs.
     * It ensures that tokens were issued by this server and have not been tampered with.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Build the secret key using the same algorithm and shared secret used in JwtService.
        SecretKeySpec secretKey = new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    /**
     * Customizes how claims inside the JWT are translated into Spring Security GrantedAuthorities.
     * This bridges the gap between the "roles" list in the token and RBAC annotations.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter listConverter = new JwtGrantedAuthoritiesConverter();

        // Instruct Spring to look for the "roles" claim instead of the default "scp" or "scope".
        listConverter.setAuthoritiesClaimName("roles");
        listConverter.setAuthorityPrefix("");

        // Roles in the JWT are already prefixed with "ROLE_" (e.g. "ROLE_CLINICIAN")
        // Clear the default "SCOPE_" prefix so they arrive in the SecurityContext as-is,
        // allowing @PreAuthorize("hasRole('CLINICIAN')") to match correctly
        listConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(listConverter);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Configures Cross-Origin Resource Sharing (CORS) to allow the Nuxt frontend
     * to communicate with the Spring Boot API from a different origin.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:3000"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        return new CookieBearerTokenResolver();
    }
}