(function () {
    "use strict";

    var SESSION_KEY = "cb_seo_session_id";
    var GUIDE_ATTRIBUTION_KEY = "cb_seo_guide_attribution";

    function randomId() {
        if (window.crypto && window.crypto.randomUUID) {
            return window.crypto.randomUUID();
        }
        return "sid_" + Math.random().toString(36).slice(2) + Date.now().toString(36);
    }

    function getSessionId() {
        try {
            var existing = window.localStorage.getItem(SESSION_KEY);
            if (existing) {
                return existing;
            }
            var created = randomId();
            window.localStorage.setItem(SESSION_KEY, created);
            return created;
        } catch (error) {
            return randomId();
        }
    }

    function normalizeSlug(value) {
        if (!value) {
            return null;
        }
        var normalized = String(value).trim().toLowerCase();
        if (!normalized) {
            return null;
        }
        if (!/^[a-z0-9_-]{1,80}$/.test(normalized)) {
            return null;
        }
        return normalized;
    }

    function getGuideAttribution() {
        try {
            var raw = window.sessionStorage.getItem(GUIDE_ATTRIBUTION_KEY);
            if (!raw) {
                return null;
            }
            var parsed = JSON.parse(raw);
            var platformSlug = normalizeSlug(parsed.platformSlug);
            var guideSlug = normalizeSlug(parsed.guideSlug);
            if (!platformSlug || !guideSlug) {
                return null;
            }
            return {
                platformSlug: platformSlug,
                guideSlug: guideSlug
            };
        } catch (error) {
            return null;
        }
    }

    function saveGuideAttribution(platformSlug, guideSlug) {
        var platform = normalizeSlug(platformSlug);
        var guide = normalizeSlug(guideSlug);
        if (!platform || !guide) {
            return;
        }
        try {
            window.sessionStorage.setItem(GUIDE_ATTRIBUTION_KEY, JSON.stringify({
                platformSlug: platform,
                guideSlug: guide
            }));
        } catch (error) {
            // noop
        }
    }

    function guessSourceChannel() {
        try {
            var params = new URLSearchParams(window.location.search || "");
            var utmSource = params.get("utm_source");
            if (utmSource) {
                return utmSource.toLowerCase();
            }
        } catch (error) {
            // noop
        }

        var referrer = document.referrer || "";
        if (!referrer) {
            return "direct";
        }
        if (referrer.indexOf("google.") >= 0 || referrer.indexOf("bing.") >= 0 || referrer.indexOf("duckduckgo.") >= 0) {
            return "organic";
        }
        if (referrer.indexOf("reddit.com") >= 0 || referrer.indexOf("x.com") >= 0 || referrer.indexOf("twitter.com") >= 0) {
            return "social";
        }
        return "referral";
    }

    function send(payload) {
        var body = JSON.stringify(payload);
        var url = "/api/seo/events";

        if (navigator.sendBeacon) {
            try {
                var blob = new Blob([body], { type: "application/json" });
                navigator.sendBeacon(url, blob);
                return;
            } catch (error) {
                // fallback to fetch
            }
        }

        fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: body,
            keepalive: true
        }).catch(function () {
            // noop
        });
    }

    function track(eventName, payload) {
        if (!eventName) {
            return;
        }
        var data = payload || {};
        send({
            eventName: eventName,
            platformSlug: data.platformSlug || null,
            guideSlug: data.guideSlug || null,
            pagePath: window.location.pathname,
            sourceChannel: data.sourceChannel || guessSourceChannel(),
            sessionId: getSessionId(),
            referrer: document.referrer || null,
            userAgent: navigator.userAgent || null
        });
    }

    function initGuideDetailTracking() {
        var node = document.querySelector("[data-seo-page='guide-detail']");
        if (!node) {
            return;
        }

        var platformSlug = node.getAttribute("data-seo-platform") || null;
        var guideSlug = node.getAttribute("data-seo-guide") || null;
        saveGuideAttribution(platformSlug, guideSlug);
        track("guide_view", {
            platformSlug: platformSlug,
            guideSlug: guideSlug
        });
    }

    function initNewCaseTracking() {
        if (window.location.pathname !== "/new") {
            return;
        }
        var params = new URLSearchParams(window.location.search || "");
        var source = params.get("src");
        if (source !== "guide" && source !== "guide_router_nomatch") {
            return;
        }
        var platformSlug = normalizeSlug(params.get("platform"));
        var guideSlug = normalizeSlug(params.get("guide")) || (source === "guide_router_nomatch" ? "router_nomatch" : null);
        saveGuideAttribution(platformSlug, guideSlug);
        track(source === "guide_router_nomatch" ? "new_case_view_from_router_nomatch" : "new_case_view_from_guide", {
            platformSlug: platformSlug,
            guideSlug: guideSlug,
            sourceChannel: source === "guide_router_nomatch" ? "guide_router" : "guide_internal"
        });
    }

    function initCaseCreatedTracking() {
        if (!window.location.pathname.startsWith("/c/")) {
            return;
        }
        var params = new URLSearchParams(window.location.search || "");
        var source = params.get("src");
        if (source !== "guide" && source !== "guide_router_nomatch") {
            return;
        }
        if (params.get("created") !== "1") {
            return;
        }

        var platformSlug = normalizeSlug(params.get("platform"));
        var guideSlug = normalizeSlug(params.get("guide")) || (source === "guide_router_nomatch" ? "router_nomatch" : null);
        if (!guideSlug) {
            return;
        }
        if (platformSlug) {
            saveGuideAttribution(platformSlug, guideSlug);
        }

        var caseCreatedFlagKey = "cb_case_created_tracked_" + window.location.pathname;
        try {
            if (window.sessionStorage.getItem(caseCreatedFlagKey) === "1") {
                return;
            }
            window.sessionStorage.setItem(caseCreatedFlagKey, "1");
        } catch (error) {
            // noop
        }

        track(source === "guide_router_nomatch" ? "case_created_from_router_nomatch" : "case_created_from_guide", {
            platformSlug: platformSlug,
            guideSlug: guideSlug,
            sourceChannel: source === "guide_router_nomatch" ? "guide_router" : "guide_internal"
        });
    }

    function initGlobalSeoEventTracking() {
        document.addEventListener("click", function (event) {
            var target = event.target && event.target.closest ? event.target.closest("[data-seo-event]") : null;
            if (!target) {
                return;
            }
            var eventName = target.getAttribute("data-seo-event");
            if (!eventName) {
                return;
            }
            var attribution = getGuideAttribution();
            var platformSlug = normalizeSlug(target.getAttribute("data-seo-platform"))
                    || (attribution ? attribution.platformSlug : null);
            var guideSlug = normalizeSlug(target.getAttribute("data-seo-guide"))
                    || (attribution ? attribution.guideSlug : null);
            if (platformSlug && guideSlug) {
                saveGuideAttribution(platformSlug, guideSlug);
            }
            track(eventName, {
                platformSlug: platformSlug,
                guideSlug: guideSlug,
                sourceChannel: (platformSlug && guideSlug) ? "guide_internal" : undefined
            });
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        initGlobalSeoEventTracking();
        initGuideDetailTracking();
        initNewCaseTracking();
        initCaseCreatedTracking();
    });

    window.cbSeoTracker = {
        track: track,
        getSessionId: getSessionId
    };
})();
