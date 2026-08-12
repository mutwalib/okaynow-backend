package com.okaynow.auth;

import com.okaynow.auth.dto.AuthResponse;
import com.okaynow.auth.dto.EmailOnlyRequest;
import com.okaynow.auth.dto.LoginRequest;
import com.okaynow.auth.dto.LoginResult;
import com.okaynow.auth.dto.MessageResponse;
import com.okaynow.auth.dto.RefreshRequest;
import com.okaynow.auth.dto.RegisterRequest;
import com.okaynow.auth.dto.RegisterResult;
import com.okaynow.auth.dto.ResetPasswordRequest;
import com.okaynow.auth.dto.VerifyEmailRequest;
import com.okaynow.auth.dto.VerifyLoginOtpRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResult> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody EmailOnlyRequest request) {
        return ResponseEntity.ok(authService.resendSignupVerification(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verify-login-otp")
    public ResponseEntity<AuthResponse> verifyLoginOtp(
            @Valid @RequestBody VerifyLoginOtpRequest request) {
        return ResponseEntity.ok(authService.verifyLoginOtp(request));
    }

    @PostMapping("/resend-login-otp")
    public ResponseEntity<MessageResponse> resendLoginOtp(
            @Valid @RequestBody EmailOnlyRequest request) {
        return ResponseEntity.ok(authService.resendLoginOtp(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody EmailOnlyRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }
}
