package com.example.demo.dispute.api;

import com.example.demo.dispute.service.SeoAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seo")
public class SeoAnalyticsController {

    private final SeoAnalyticsService seoAnalyticsService;

    public SeoAnalyticsController(SeoAnalyticsService seoAnalyticsService) {
        this.seoAnalyticsService = seoAnalyticsService;
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, String>> trackEvent(
            @RequestBody SeoEventTrackRequest request,
            HttpServletRequest servletRequest
    ) {
        String userAgent = servletRequest.getHeader("User-Agent");
        seoAnalyticsService.track(request, userAgent);
        return ResponseEntity.accepted().body(Map.of("status", "accepted"));
    }

    @GetMapping("/kpi")
    public ResponseEntity<SeoKpiResponse> kpi(
            @RequestParam(name = "days", required = false, defaultValue = "7") int days
    ) {
        return ResponseEntity.ok(seoAnalyticsService.summarize(days));
    }
}
