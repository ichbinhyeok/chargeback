package com.example.demo.dispute;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SeoAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tracksSeoEventsAndAggregatesKpi() throws Exception {
        mockMvc.perform(post("/api/seo/events")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventName": "guide_view",
                                  "platformSlug": "stripe",
                                  "guideSlug": "fraud",
                                  "pagePath": "/guides/stripe/fraud",
                                  "sourceChannel": "organic",
                                  "sessionId": "sid_1",
                                  "referrer": "https://google.com/search?q=stripe+fraud+evidence"
                                }
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(jsonPath("$.status").value("accepted"));

        mockMvc.perform(post("/api/seo/events")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventName": "guide_start_case_click",
                                  "platformSlug": "stripe",
                                  "guideSlug": "fraud",
                                  "pagePath": "/guides/stripe/fraud",
                                  "sourceChannel": "organic",
                                  "sessionId": "sid_1",
                                  "referrer": "https://google.com/search?q=stripe+fraud+evidence"
                                }
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/seo/events")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventName": "new_case_view_from_guide",
                                  "platformSlug": "stripe",
                                  "guideSlug": "fraud",
                                  "pagePath": "/new",
                                  "sourceChannel": "guide_internal",
                                  "sessionId": "sid_1",
                                  "referrer": "http://localhost/guides/stripe/fraud"
                                }
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/seo/events")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventName": "case_created_from_guide",
                                  "platformSlug": "stripe",
                                  "guideSlug": "fraud",
                                  "pagePath": "/c/case_example",
                                  "sourceChannel": "guide_internal",
                                  "sessionId": "sid_1",
                                  "referrer": "http://localhost/new?src=guide"
                                }
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/seo/events")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventName": "guide_export_click",
                                  "platformSlug": "stripe",
                                  "guideSlug": "fraud",
                                  "pagePath": "/c/case_example/export",
                                  "sourceChannel": "guide_internal",
                                  "sessionId": "sid_1",
                                  "referrer": "http://localhost/c/case_example/export"
                                }
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/seo/events")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventName": "guide_router_nomatch",
                                  "platformSlug": "shopify",
                                  "pagePath": "/guides/router",
                                  "sourceChannel": "guide_router",
                                  "sessionId": "sid_router_1",
                                  "queryText": "shopify embedded object unsupported pdf",
                                  "matchTarget": null
                                }
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/seo/events")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventName": "new_case_view_from_router_nomatch",
                                  "platformSlug": "shopify",
                                  "guideSlug": "router_nomatch",
                                  "pagePath": "/new",
                                  "sourceChannel": "guide_router",
                                  "sessionId": "sid_router_1",
                                  "referrer": "http://localhost/guides"
                                }
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/seo/events")
                        .contentType("application/json")
                        .content("""
                                {
                                  "eventName": "case_created_from_router_nomatch",
                                  "platformSlug": "shopify",
                                  "guideSlug": "router_nomatch",
                                  "pagePath": "/c/case_router",
                                  "sourceChannel": "guide_router",
                                  "sessionId": "sid_router_1",
                                  "referrer": "http://localhost/new?src=guide_router_nomatch"
                                }
                                """.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/seo/kpi")
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guideViewCount").value(1))
                .andExpect(jsonPath("$.startCaseClickCount").value(1))
                .andExpect(jsonPath("$.newCaseViewFromGuideCount").value(1))
                .andExpect(jsonPath("$.caseCreatedFromGuideCount").value(1))
                .andExpect(jsonPath("$.guideExportClickCount").value(1))
                .andExpect(jsonPath("$.guideToStartCaseClickRate").value(1.0))
                .andExpect(jsonPath("$.startCaseClickToNewCaseViewRate").value(1.0))
                .andExpect(jsonPath("$.newCaseViewToCaseCreatedRate").value(1.0))
                .andExpect(jsonPath("$.caseCreatedToGuideExportClickRate").value(1.0))
                .andExpect(jsonPath("$.guideRouterNoMatchCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.newCaseViewFromRouterNoMatchCount").value(1))
                .andExpect(jsonPath("$.caseCreatedFromRouterNoMatchCount").value(1))
                .andExpect(jsonPath("$.routerNoMatchToNewCaseViewRate").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.routerNoMatchToCaseCreatedRate").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.guideFunnels[0].platform").value("stripe"))
                .andExpect(jsonPath("$.guideFunnels[0].guideSlug").value("fraud"))
                .andExpect(jsonPath("$.guideFunnels[0].guideViews").value(1))
                .andExpect(jsonPath("$.guideFunnels[0].caseCreated").value(1))
                .andExpect(jsonPath("$.guideFunnels[0].exportClicks").value(1))
                .andExpect(jsonPath("$.guideFunnels[0].viewToCreatedRate").value(1.0))
                .andExpect(jsonPath("$.guideFunnels[0].createdToExportRate").value(1.0))
                .andExpect(jsonPath("$.priorityGuidesToImprove.length()").value(0))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("shopify embedded object unsupported pdf")));
    }

    @Test
    void kpiDashboardPageRendersNoindexMeta() throws Exception {
        mockMvc.perform(get("/seo/kpi"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SEO KPI Dashboard")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("meta name=\"robots\" content=\"noindex,nofollow,noarchive\"")));
    }
}
