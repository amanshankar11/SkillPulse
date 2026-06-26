package com.skillpulse.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;

@Validated
@RestController
public class AuthController {
    private final AuthService authService;
    private final PasswordRecoveryService passwordRecoveryService;

    public AuthController(AuthService authService, PasswordRecoveryService passwordRecoveryService) {
        this.authService = authService;
        this.passwordRecoveryService = passwordRecoveryService;
    }

    @PostMapping("/api/auth/register")
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/api/auth/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/api/auth/logout")
    public Map<String, Object> logout(@RequestBody AuthDtos.LogoutRequest request) {
        authService.logout(request.getToken());
        return Collections.<String, Object>singletonMap("ok", true);
    }

    @PostMapping("/api/auth/change-password")
    public Map<String, Object> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(request);
        return Collections.<String, Object>singletonMap("message", "Password updated successfully.");
    }

    @PostMapping("/api/auth/delete-account")
    public Map<String, Object> deleteAccount(@Valid @RequestBody AuthDtos.DeleteAccountRequest request) {
        authService.deleteAccount(request);
        return Collections.<String, Object>singletonMap("message", "Account deleted successfully.");
    }

    @PostMapping("/api/auth/update-profile")
    public AuthDtos.AuthResponse updateProfile(@Valid @RequestBody AuthDtos.UpdateProfileRequest request) {
        return authService.updateProfile(request);
    }

    @PostMapping("/api/auth/forgot-password")
    public Map<String, Object> forgotPassword(@Valid @RequestBody PasswordRecoveryDtos.ForgotPasswordRequest request) {
        passwordRecoveryService.requestReset(request.getEmail());
        return Collections.<String, Object>singletonMap("message",
                "If an account exists for this email, a reset link has been sent.");
    }

    @PostMapping("/api/auth/reset-password")
    public Map<String, Object> resetPassword(@Valid @RequestBody PasswordRecoveryDtos.ResetPasswordRequest request) {
        passwordRecoveryService.resetPassword(request.getToken(), request.getNewPassword());
        return Collections.<String, Object>singletonMap("message", "Password reset successfully. You can now sign in.");
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<?> me(@RequestParam("token") String token) {
        return authService.findByToken(token)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(new AuthDtos.AuthResponse(token, user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Collections.singletonMap("message", "Session expired. Please sign in again.")));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException
                ? "Please check the submitted fields."
                : ex.getMessage();
        return ResponseEntity.badRequest().body(Collections.singletonMap("message", message));
    }
}
