package com.maths.teacher.security;

import com.maths.teacher.catalog.exception.ErrorMessages;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    /**
     * Returns the current user id from the JWT (set by JwtAuthenticationFilter).
     * Throws 401 if not authenticated.
     */
    public String requireUserId(String authorizationHeader) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ErrorMessages.MISSING_AUTHORIZATION_HEADER);
        }
        return auth.getPrincipal().toString();
    }
}
