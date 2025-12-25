package com.stolink.backend.domain.user.controller;

import com.stolink.backend.domain.user.dto.LoginRequest;
import com.stolink.backend.domain.user.dto.RegisterRequest;
import com.stolink.backend.domain.user.dto.UserResponse;
import com.stolink.backend.domain.user.service.AuthService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(@RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ApiResponse.created(user);
    }

    @PostMapping("/login")
    public ApiResponse<UserResponse> login(@RequestBody LoginRequest request) {
        UserResponse user = authService.login(request);
        return ApiResponse.ok(user);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@RequestHeader("X-User-Id") UUID userId) {
        UserResponse user = authService.getUser(userId);
        return ApiResponse.ok(user);
    }

    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateMe(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String avatarUrl) {
        UserResponse user = authService.updateUser(userId, nickname, avatarUrl);
        return ApiResponse.ok(user);
    }
}
