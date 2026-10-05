package org.example.notesharing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.notesharing.dto.auth.LoginRequestDto;
import org.example.notesharing.dto.auth.RefreshRequestDto;
import org.example.notesharing.dto.auth.RegisterRequestDto;
import org.example.notesharing.dto.auth.TokenPairDto;
import org.example.notesharing.dto.common.ApiResponse;
import org.example.notesharing.security.JwtPrincipal;
import org.example.notesharing.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenPairDto>> register(
            @Valid @RequestBody RegisterRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(authService.register(request), "Registered"));
    }

    @PostMapping("/login")
    public ApiResponse<TokenPairDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ApiResponse.of(authService.login(request), "Logged in");
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPairDto> refresh(@Valid @RequestBody RefreshRequestDto request) {
        return ApiResponse.of(authService.refresh(request), "Token refreshed");
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal JwtPrincipal principal) {
        authService.logout(principal.userId());
        return ResponseEntity.noContent().build();
    }
}
