package com.maths.teacher.catalog.service;

import com.maths.teacher.auth.domain.User;
import com.maths.teacher.auth.repository.UserRepository;
import com.maths.teacher.catalog.web.dto.RegisterStudentRequest;
import com.maths.teacher.catalog.web.dto.UserSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserSearchResponse registerStudent(RegisterStudentRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String mobile = request.getMobileNumber() != null ? request.getMobileNumber().trim() : "";

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (userRepository.findByMobileNumber(mobile).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mobile number already registered");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        User user = new User(
                request.getFirstName().trim(),
                request.getLastName().trim(),
                email,
                mobile,
                passwordHash
        );
        user = userRepository.save(user);

        logger.info("Admin registered new student: id={}, mobile={}", user.getId(), mobile);
        return toResponse(user);
    }

    public UserSearchResponse searchUser(String query) {
        return userRepository.findByEmailOrMobileNumber(query.trim())
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserSearchResponse toResponse(User user) {
        return new UserSearchResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getMobileNumber()
        );
    }
}
