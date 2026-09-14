package com.ticketrush.service;

import com.ticketrush.dto.AuthResponse;
import com.ticketrush.dto.LoginRequest;
import com.ticketrush.dto.RegisterRequest;
import com.ticketrush.entity.User;
import com.ticketrush.enums.Role;
import com.ticketrush.exception.InvalidOperationException;
import com.ticketrush.repository.UserRepository;
import com.ticketrush.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ticketrush.dto.ChangePasswordRequest;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Xử lý logic đăng ký tài khoản mới. Kiểm tra trùng lặp email/username và mã hóa mật khẩu.
     */
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new InvalidOperationException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new InvalidOperationException("Email already exists");
        }
        
        if (request.getDateOfBirth() == null) {
            throw new InvalidOperationException("Date of birth is required");
        }
        if (java.time.Period.between(request.getDateOfBirth(), java.time.LocalDate.now()).getYears() < 16) {
            throw new InvalidOperationException("You must be at least 16 years old to register");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .role(Role.ROLE_CUSTOMER)
                .isActive(true)
                .build();

        userRepository.save(user);

        return AuthResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }

    public boolean checkUsernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean checkEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Xử lý đăng nhập, gọi AuthenticationManager để xác thực và sinh ra JWT Token.
     */
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        String token = jwtTokenProvider.generateToken(authentication);
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidOperationException("User not found"));

        return buildAuthResponse(user, token);
    }

    /**
     * Đổi mật khẩu của người dùng, yêu cầu mật khẩu cũ phải khớp.
     */
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidOperationException("User not found"));
                
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new InvalidOperationException("Old password does not match");
        }
        
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * Lấy thông tin User hiện tại từ database dựa trên username (thường trích xuất từ JWT token).
     */
    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidOperationException("User not found"));
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .build();
    }
}
