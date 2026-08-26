package com.petassistant.business.controller;

import java.security.Principal;

import com.petassistant.business.data.dto.request.UpdateUserRoleRequest;
import com.petassistant.business.data.dto.request.UpdateUserStatusRequest;
import com.petassistant.business.data.dto.response.AdminAuditPageResponse;
import com.petassistant.business.data.dto.response.AdminUserPageResponse;
import com.petassistant.business.data.dto.response.AdminUserResponse;
import com.petassistant.business.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN 专用用户、角色和审计入口。 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public AdminUserPageResponse users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(keyword, role, status, page, size);
    }

    @PatchMapping("/users/{userId}/role")
    public AdminUserResponse updateRole(
            Principal principal,
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        return service.updateRole(principal.getName(), userId, request);
    }

    @PatchMapping("/users/{userId}/status")
    public AdminUserResponse updateStatus(
            Principal principal,
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return service.updateStatus(principal.getName(), userId, request);
    }

    @GetMapping("/audit-logs")
    public AdminAuditPageResponse audits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.audits(page, size);
    }
}
