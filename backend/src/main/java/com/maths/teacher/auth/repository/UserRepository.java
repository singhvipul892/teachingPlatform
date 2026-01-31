package com.maths.teacher.auth.repository;

import com.maths.teacher.auth.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByMobileNumber(String mobileNumber);

    /**
     * Find user by email or mobile number (for login).
     * Tries email first, then mobile.
     */
    default Optional<User> findByEmailOrMobileNumber(String emailOrMobile) {
        return findByEmail(emailOrMobile)
                .or(() -> findByMobileNumber(emailOrMobile));
    }
}
