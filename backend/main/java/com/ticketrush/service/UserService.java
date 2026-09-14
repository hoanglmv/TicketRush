package com.ticketrush.service;

import com.ticketrush.dto.UserProfileResponse;
import com.ticketrush.dto.UserProfileUpdateRequest;
import com.ticketrush.entity.User;
import com.ticketrush.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Lấy thông tin cá nhân của người dùng dựa trên username.
     */
    public UserProfileResponse getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToResponse(user);
    }

    /**
     * Cập nhật thông tin cá nhân (họ tên, số điện thoại, ngày sinh, giới tính).
     */
    @Transactional
    public UserProfileResponse updateUserProfile(String username, UserProfileUpdateRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getDateOfBirth() != null) user.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) user.setGender(request.getGender());

        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    private UserProfileResponse mapToResponse(User user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setPhone(user.getPhone());
        response.setDateOfBirth(user.getDateOfBirth());
        response.setGender(user.getGender());
        response.setRole(user.getRole().name());
        return response;
    }
}
