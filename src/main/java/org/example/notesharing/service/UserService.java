package org.example.notesharing.service;

import lombok.RequiredArgsConstructor;
import org.example.notesharing.dto.common.PageResponse;
import org.example.notesharing.dto.user.UpdateUserRequestDto;
import org.example.notesharing.dto.user.UserResponseDto;
import org.example.notesharing.dto.user.UserSummaryDto;
import org.example.notesharing.entity.User;
import org.example.notesharing.exception.BusinessRuleException;
import org.example.notesharing.exception.ConflictException;
import org.example.notesharing.exception.ResourceNotFoundException;
import org.example.notesharing.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public UserResponseDto getById(Long userId) {
        return toResponse(require(userId));
    }

    @Transactional
    public UserResponseDto update(Long userId, UpdateUserRequestDto request) {
        User user = require(userId);

        if (request.username() != null && !request.username().isBlank()) {
            String username = request.username().trim();
            if (!username.equalsIgnoreCase(user.getUsername())
                    && users.existsByUsernameIgnoreCase(username)) {
                throw new ConflictException("Username", username);
            }
            user.setUsername(username);
        }

        return toResponse(users.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryDto> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            throw new BusinessRuleException("A search query 'q' is required.");
        }
        return PageResponse.from(
            users.search(query.trim(), pageable),
            user -> new UserSummaryDto(user.getId(), user.getUsername()));
    }

    private User require(Long userId) {
        return users.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private static UserResponseDto toResponse(User user) {
        return new UserResponseDto(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getBod(),
            user.getGender());
    }
}
