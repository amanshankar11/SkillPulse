package com.skillpulse.auth;

import com.skillpulse.practice.UserPracticeAttemptRepository;
import com.skillpulse.notification.NotificationPreferenceRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserPracticeAttemptRepository practiceAttempts;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final NotificationPreferenceRepository notificationPreferences;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Map<String, Long> sessions = new ConcurrentHashMap<String, Long>();

    public AuthService(UserRepository users, UserPracticeAttemptRepository practiceAttempts,
                       PasswordResetTokenRepository passwordResetTokens,
                       NotificationPreferenceRepository notificationPreferences) {
        this.users = users;
        this.practiceAttempts = practiceAttempts;
        this.passwordResetTokens = passwordResetTokens;
        this.notificationPreferences = notificationPreferences;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        AppUser user = new AppUser();
        user.setName(request.getName().trim());
        String[] names = splitName(request.getName());
        user.setFirstName(names[0]);
        user.setLastName(names[1]);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(request.getPassword()));
        users.save(user);
        return issueSession(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new IllegalArgumentException("No account found with this email."));
        if (!encoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect password. Please try again.");
        }
        return issueSession(user);
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    @Transactional
    public void changePassword(AuthDtos.ChangePasswordRequest request) {
        AppUser user = requireUser(request.getToken());
        if (!encoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (encoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from your current password.");
        }

        user.setPasswordHash(encoder.encode(request.getNewPassword()));
        users.save(user);
    }

    @Transactional
    public AuthDtos.AuthResponse updateProfile(AuthDtos.UpdateProfileRequest request) {
        AppUser user = requireUser(request.getToken());
        String email = normalizeEmail(request.getEmail());
        Optional<AppUser> existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        String firstName = clean(request.getFirstName());
        String lastName = clean(request.getLastName());
        if (firstName.isEmpty() || lastName.isEmpty()) {
            throw new IllegalArgumentException("First name and last name are required.");
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setName(firstName + " " + lastName);
        user.setEmail(email);
        user.setPhoneCountryCode(clean(request.getPhoneCountryCode()));
        user.setPhoneNumber(clean(request.getPhoneNumber()));
        user.setProfilePhoto(cleanPhoto(request.getProfilePhoto()));
        users.save(user);
        return new AuthDtos.AuthResponse(request.getToken(), user);
    }

    @Transactional
    public void deleteAccount(AuthDtos.DeleteAccountRequest request) {
        AppUser user = requireUser(request.getToken());
        if (!encoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Password is incorrect. Account was not deleted.");
        }

        practiceAttempts.deleteByUserId(user.getId());
        passwordResetTokens.deleteByUserId(user.getId());
        notificationPreferences.deleteByUserId(user.getId());
        users.delete(user);
        sessions.entrySet().removeIf(entry -> entry.getValue().equals(user.getId()));
    }

    public Optional<AppUser> findByToken(String token) {
        Long userId = sessions.get(token);
        return userId == null ? Optional.<AppUser>empty() : users.findById(userId);
    }

    public AppUser requireUser(String token) {
        return findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Session expired. Please sign in again."));
    }

    public AppUser requireAdmin(String token) {
        AppUser user = requireUser(token);
        if (!AppUser.Role.ADMIN.equals(user.getRole())) {
            throw new IllegalArgumentException("Administrator access is required.");
        }
        return user;
    }

    @Transactional
    public void setPasswordFromRecovery(AppUser user, String newPassword) {
        if (user == null) throw new IllegalArgumentException("Account not found.");
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Choose a password different from your current password.");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        sessions.entrySet().removeIf(entry -> entry.getValue().equals(user.getId()));
    }

    private AuthDtos.AuthResponse issueSession(AppUser user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, user.getId());
        return new AuthDtos.AuthResponse(token, user);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanPhoto(String value) {
        String photo = clean(value);
        if (photo.isEmpty()) {
            return null;
        }
        if (photo.length() > 500000) {
            throw new IllegalArgumentException("Profile photo is too large. Please choose a smaller image.");
        }
        return photo;
    }

    private String[] splitName(String name) {
        String cleanName = clean(name);
        int space = cleanName.indexOf(' ');
        if (space < 0) {
            return new String[] { cleanName, "" };
        }
        return new String[] { cleanName.substring(0, space).trim(), cleanName.substring(space + 1).trim() };
    }
}
