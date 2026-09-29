package com.financetracker.dashboard;

import com.financetracker.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Main Responsibility: Expose authenticated GET /dashboard aggregates.
 *
 * User id always comes from JWT via CurrentUser. Optional from/to filter
 * expense_date inclusively. Response includes currency/merchant totals plus
 * the line leaf/parent breakdown. No SecurityConfig change needed.
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService dashboardService, CurrentUser currentUser) {
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
    }

    /**
     * Return expense aggregates for the current user.
     * Optional from/to (inclusive expense_date) narrow the period.
     */
    @GetMapping
    public DashboardResponse getDashboard(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return dashboardService.getDashboard(currentUser.getUserId(), from, to);
    }
}
