package com.maths.teacher.auth.service;

import com.maths.teacher.auth.domain.User;
import com.maths.teacher.auth.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security UserDetailsService implementation.
 * Loads User entities from the database and returns them as UserDetails for Spring Security.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads a user by username (email or mobile number).
     *
     * @param username the email or mobile number
     * @return UserDetails (User entity that implements UserDetails)
     * @throws UsernameNotFoundException if user not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Try to find by email first, then by mobile number
        return userRepository.findByEmail(username)
                .orElseGet(() -> userRepository.findByMobileNumber(username)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username)));
    }

    /**
     * Loads a user by ID.
     *
     * @param userId the user ID
     * @return User entity
     * @throws UsernameNotFoundException if user not found
     */
    public User loadUserById(Long userId) throws UsernameNotFoundException {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
    }
}
