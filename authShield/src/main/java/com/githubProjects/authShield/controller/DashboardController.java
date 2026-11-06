package com.githubProjects.authShield.controller;

import com.githubProjects.authShield.dto.AuthResponses;
import com.githubProjects.authShield.service.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public AuthResponses.DashboardOverview overview(Authentication authentication) {
        return dashboardService.getOverview(authentication.getName());
    }
}