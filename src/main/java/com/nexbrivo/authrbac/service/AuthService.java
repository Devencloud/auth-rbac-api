package com.nexbrivo.authrbac.service;

import com.nexbrivo.authrbac.dto.LoginRequest;
import com.nexbrivo.authrbac.dto.LoginResponse;
import com.nexbrivo.authrbac.dto.RegisterRequest;
import com.nexbrivo.authrbac.dto.UserResponse;
import com.nexbrivo.authrbac.entity.RefreshToken;
import com.nexbrivo.authrbac.entity.User;
import com.nexbrivo.authrbac.repository.RefreshTokenRepository;
import com.nexbrivo.authrbac.repository.UserRepository;
import com.nexbrivo.authrbac.security.JwtService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenExpirationMs;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User saved = userRepository.save(new User(request.getEmail(), hashedPassword));

        return new UserResponse(saved.getId(), saved.getEmail(), saved.getRole());
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw invalidCredentials();
        }

        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole());

        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID().toString(),
                user,
                Instant.now().plusMillis(refreshTokenExpirationMs));
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(accessToken, refreshToken.getToken());
    }

    // noRollbackFor keeps the delete of an expired token from being undone by the 401 exception
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse refresh(String tokenValue) {
        RefreshToken stored = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(this::invalidRefreshToken);

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw invalidRefreshToken();
        }

        User user = stored.getUser();
        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole());

        return new LoginResponse(accessToken, stored.getToken());
    }

    public void logout(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue)
                .ifPresent(refreshTokenRepository::delete);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    private ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
    }
}