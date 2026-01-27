package com.maths.teacher.security;

import com.maths.teacher.catalog.exception.ErrorMessages;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    public String requireUserId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ErrorMessages.MISSING_AUTHORIZATION_HEADER);
        }
        // Placeholder: replace with real JWT validation and user extraction.
        return authorizationHeader.replace("Bearer", "").trim();
    }
}
