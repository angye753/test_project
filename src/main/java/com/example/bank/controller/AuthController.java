package com.example.bank.controller;

import com.example.bank.security.JwtTokenProvider;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController - Handles JWT token generation and authentication.
 * Provides login endpoint for obtaining JWT tokens.
 */
@Slf4j
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Login endpoint - Authenticates user and returns JWT token.
     * 
     * @param loginRequest Username and password
     * @return JWT token if authentication successful
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.getUsername());
        
        try {
            // Note: In production, you would load user details from a User repository
            // and verify the password. For demo purposes, we're using a simple approach.
            
            var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );

            // Extract role from authentication (would come from UserDetails in production)
            String role = extractRoleFromAuthentication(authentication);
            
            String token = jwtTokenProvider.generateToken(loginRequest.getUsername(), role);
            
            log.info("User logged in successfully: {}", loginRequest.getUsername());
            return ResponseEntity.ok(new LoginResponse(token));

        } catch (AuthenticationException ex) {
            log.warn("Authentication failed for user: {}", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse(null));
        }
    }

    /**
     * Extracts the role from the authentication object.
     * The role comes from the UserDetails loaded by CustomUserDetailsService.
     */
    private String extractRoleFromAuthentication(org.springframework.security.core.Authentication auth) {
        // Extract role from granted authorities (e.g., "ROLE_ADMIN" -> "ADMIN")
        return auth.getAuthorities().stream()
            .map(ga -> ga.getAuthority().replace("ROLE_", ""))
            .findFirst()
            .orElse("ACCOUNT_HOLDER");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
    }
}
