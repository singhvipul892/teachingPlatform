package com.maths.teacher.catalog.web.admin;

import com.maths.teacher.catalog.service.AdminUserService;
import com.maths.teacher.catalog.web.dto.RegisterStudentRequest;
import com.maths.teacher.catalog.web.dto.UserSearchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API endpoints for user management.
 * All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * Registers a new student account on behalf of the admin.
     * Used for students who pay directly (cash/UPI/bank transfer).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserSearchResponse registerStudent(@RequestBody RegisterStudentRequest request) {
        return adminUserService.registerStudent(request);
    }

    /**
     * Looks up a registered user by mobile number or email.
     *
     * @param q mobile number or email to search
     */
    @GetMapping
    public UserSearchResponse searchUser(@RequestParam String q) {
        return adminUserService.searchUser(q);
    }
}
