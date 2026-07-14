package com.pavithra.queueless.service;

import com.pavithra.queueless.dto.auth.AuthResponse;
import com.pavithra.queueless.dto.auth.LoginRequest;
import com.pavithra.queueless.dto.auth.RegisterRequest;
import com.pavithra.queueless.entity.Role;
import com.pavithra.queueless.entity.User;
import com.pavithra.queueless.exception.BadRequestException;
import com.pavithra.queueless.repository.UserRepository;
import com.pavithra.queueless.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_createsUserAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("Pavithra Nair", "pavithra@test.com", "secret123", "9999999999", Role.CUSTOMER);

        when(userRepository.existsByEmail("pavithra@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(any(), any(Map.class))).thenReturn("fake-jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("fake-jwt-token");
        assertThat(response.email()).isEqualTo("pavithra@test.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_rejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("Someone", "taken@test.com", "secret123", null, Role.CUSTOMER);
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_returnsTokenForValidCredentials() {
        LoginRequest request = new LoginRequest("pavithra@test.com", "secret123");
        User user = User.builder().id(1L).fullName("Pavithra Nair").email("pavithra@test.com")
                .passwordHash("hashed").role(Role.CUSTOMER).build();

        when(userRepository.findByEmail("pavithra@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(), any(Map.class))).thenReturn("fake-jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("fake-jwt-token");
        assertThat(response.userId()).isEqualTo(1L);
        verify(authenticationManager).authenticate(any());
    }
}
