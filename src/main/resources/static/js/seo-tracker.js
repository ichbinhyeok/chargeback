(function () {
    "use strict";

    var SESSION_KEY = "cb_seo_session_id";

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
        track("guide_view", {
            platformSlug: platformSlug,
            guideSlug: guideSlug
        });

        var clickTargets = document.querySelectorAll("[data-seo-event]");
        clickTargets.forEach(function (element) {
            element.addEventListener("click", function () {
                track(element.getAttribute("data-seo-event"), {
                    platformSlug: element.getAttribute("data-seo-platform") || platformSlug,
                    guideSlug: element.getAttribute("data-seo-guide") || guideSlug
                });
            });
        });
    }

    function initNewCaseTracking() {
        if (window.location.pathname !== "/new") {
            return;
        }
        var params = new URLSearchParams(window.location.search || "");
        if (params.get("src") !== "guide") {
            return;
        }
        track("new_case_view_from_guide", {
            platformSlug: params.get("platform"),
            guideSlug: params.get("guide"),
            sourceChannel: "guide_internal"
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        initGuideDetailTracking();
        initNewCaseTracking();
    });

    window.cbSeoTracker = {
        track: track,
        getSessionId: getSessionId
    };
})();
