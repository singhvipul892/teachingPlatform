package com.maths.teacher.auth.repository;

import com.maths.teacher.auth.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.passwordHash = :hash WHERE u.id = :id")
    void updatePasswordHash(@Param("id") Long id, @Param("hash") String hash);
}
