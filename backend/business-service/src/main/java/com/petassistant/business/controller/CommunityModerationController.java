package com.petassistant.business.controller;

import java.security.Principal;
import java.time.LocalDate;

import com.petassistant.business.data.dto.request.ModerateCommunityReportRequest;
import com.petassistant.business.data.dto.response.CommunityAnalyticsResponse;
import com.petassistant.business.data.dto.response.CommunityReportPageResponse;
import com.petassistant.business.data.dto.response.CommunityReportResponse;
import com.petassistant.business.service.CommunitySocialService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN 与 MODERATOR 共用的举报治理入口。 */
@RestController
@RequestMapping("/api/v1/moderation/community")
public class CommunityModerationController {

    private final CommunitySocialService service;

    public CommunityModerationController(CommunitySocialService service) {
        this.service = service;
    }

    @GetMapping("/reports")
    public CommunityReportPageResponse reports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.reports(status, page, size);
    }

    @PutMapping("/reports/{reportId}")
    public CommunityReportResponse moderate(
            Principal principal,
            @PathVariable String reportId,
            @Valid @RequestBody ModerateCommunityReportRequest request
    ) {
        return service.moderate(principal.getName(), reportId, request);
    }

    @GetMapping("/analytics/today")
    public CommunityAnalyticsResponse analytics() {
        return service.analytics(LocalDate.now());
    }
}
