package com.maths.teacher.catalog.web.admin;

import com.maths.teacher.catalog.service.AdminReportService;
import com.maths.teacher.catalog.web.dto.RevenueReportResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin reporting. All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    /**
     * Revenue as recorded in the payment ledger, split by how the money arrived
     * and by course. Replaces the dashboard's old price × head-count estimate.
     */
    @GetMapping("/revenue")
    public RevenueReportResponse getRevenue() {
        return adminReportService.getRevenueReport();
    }
}
