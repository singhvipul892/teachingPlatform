package com.maths.teacher.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maths.teacher.auth.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Invoked by Spring Security whenever an unauthenticated request hits an endpoint that
 * requires authentication (missing, expired, or malformed JWT). Returns a proper 401
 * with a JSON body instead of the framework's default bare 403.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        new ErrorResponse("Session expired. Please log in again.")));
    }
}
