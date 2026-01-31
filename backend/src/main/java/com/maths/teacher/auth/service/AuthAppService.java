package com.maths.teacher.auth.service;

import com.maths.teacher.auth.domain.User;
import com.maths.teacher.auth.repository.UserRepository;
import com.maths.teacher.auth.web.AuthResponse;
import com.maths.teacher.auth.web.LoginRequest;
import com.maths.teacher.auth.web.SignupRequest;
import com.maths.teacher.catalog.exception.ErrorMessages;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthAppService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthAppService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String mobile = request.getMobileNumber() != null ? request.getMobileNumber().trim() : "";

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ErrorMessages.EMAIL_ALREADY_REGISTERED);
        }
        if (userRepository.findByMobileNumber(mobile).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ErrorMessages.MOBILE_ALREADY_REGISTERED);
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

        String token = jwtService.createToken(user.getId(), user.getEmail());
        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMobileNumber()
        );
    }

    /**
     * Login by email or mobile. Returns specific error messages:
     * - "No account found with this email or mobile number." when identifier does not exist
     * - "Password is invalid." when password is wrong
     */
    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        if (username.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email or mobile number is required.");
        }

        Optional<User> userOpt = userRepository.findByEmailOrMobileNumber(username);
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ErrorMessages.NO_ACCOUNT_FOUND_EMAIL_OR_MOBILE);
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ErrorMessages.PASSWORD_INVALID);
        }

        String token = jwtService.createToken(user.getId(), user.getEmail());
        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMobileNumber()
        );
    }
}
