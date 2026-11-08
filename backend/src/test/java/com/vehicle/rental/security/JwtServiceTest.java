package com.vehicle.rental.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    @Test
    void testGenerateAndValidateToken() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "mySecretKeyForJwtTokenGenerationThatIsLongEnoughForHS256Algorithm");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L);
        jwtService.initSigningKey();

        String token = jwtService.generateToken(1L, "test@example.com", "CUSTOMER");
        assertNotNull(token);
        assertTrue(token.length() > 0);

        Claims claims = jwtService.validateToken(token);
        assertEquals("1", claims.getSubject());
        assertEquals("test@example.com", claims.get("email"));
        assertEquals("CUSTOMER", claims.get("role"));
    }

    @Test
    void testGetUserIdFromToken() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "mySecretKeyForJwtTokenGenerationThatIsLongEnoughForHS256Algorithm");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L);
        jwtService.initSigningKey();

        String token = jwtService.generateToken(1L, "test@example.com", "CUSTOMER");
        Long userId = jwtService.getUserIdFromToken(token);
        assertEquals(1L, userId);
    }

    @Test
    void testGetRoleFromToken() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "mySecretKeyForJwtTokenGenerationThatIsLongEnoughForHS256Algorithm");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L);
        jwtService.initSigningKey();

        String token = jwtService.generateToken(1L, "test@example.com", "ADMIN");
        String role = jwtService.getRoleFromToken(token);
        assertEquals("ADMIN", role);
    }

    @Test
    void testInvalidTokenRejected() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "mySecretKeyForJwtTokenGenerationThatIsLongEnoughForHS256Algorithm");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L);
        jwtService.initSigningKey();

        assertThrows(Exception.class, () -> jwtService.validateToken("invalid.token.here"));
    }
}
