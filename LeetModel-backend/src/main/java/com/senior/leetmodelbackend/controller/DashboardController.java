package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.common.annotation.RequirePermission;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.vo.admin.DashboardVO;
import com.senior.leetmodelbackend.service.DashboardService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@AllArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @RequirePermission("DASHBOARD_VIEW")
    @GetMapping("/dashboard")
    public Result<DashboardVO> getDashboard() {
        return Result.success(dashboardService.getDashboard());
    }
}
