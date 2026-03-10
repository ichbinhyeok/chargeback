(function () {
    const STORAGE_KEY = "cb_recent_cases_v1";
    const OPT_IN_KEY = "cb_recent_cases_opt_in_v1";
    const LIMIT = 30;
    const TTL_DAYS = 7;
    const TTL_MS = TTL_DAYS * 24 * 60 * 60 * 1000;

    function canUseStorage() {
        try {
            const probeKey = "__cb_probe__";
            localStorage.setItem(probeKey, "1");
            localStorage.removeItem(probeKey);
            return true;
        } catch (_) {
            return false;
        }
    }

    function parse() {
        if (!canUseStorage()) {
            return [];
        }
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            if (!raw) {
                return [];
            }
            const parsed = JSON.parse(raw);
            if (!Array.isArray(parsed)) {
                return [];
            }
            const now = Date.now();
            return parsed
                .map((entry) => ({
                    token: normalizeToken(entry && entry.token),
                    path: entry && entry.path ? String(entry.path) : "",
                    platform: entry && entry.platform ? String(entry.platform) : "",
                    state: entry && entry.state ? String(entry.state) : "",
                    touchedAt: entry && entry.touchedAt ? String(entry.touchedAt) : ""
                }))
                .filter((entry) => {
                    if (!entry.token) {
                        return false;
                    }
                    const touchedAt = Date.parse(entry.touchedAt);
                    return Number.isFinite(touchedAt) && now - touchedAt <= TTL_MS;
                });
        } catch (_) {
            return [];
        }
    }

    function persist(entries) {
        if (!canUseStorage()) {
            return;
        }
        localStorage.setItem(STORAGE_KEY, JSON.stringify(entries.slice(0, LIMIT)));
    }

    function normalizeToken(tokenOrUrl) {
        if (!tokenOrUrl) {
            return "";
        }
        const raw = String(tokenOrUrl).trim();
        const matched = raw.match(/case_[A-Za-z0-9]+/);
        return matched ? matched[0] : "";
    }

    function list() {
        return parse().sort((a, b) => {
            const left = Date.parse(a.touchedAt || "") || 0;
            const right = Date.parse(b.touchedAt || "") || 0;
            return right - left;
        });
    }

    function isEnabled() {
        if (!canUseStorage()) {
            return false;
        }
        return localStorage.getItem(OPT_IN_KEY) === "1";
    }

    function setEnabled(enabled) {
        if (!canUseStorage()) {
            return;
        }
        if (enabled) {
            localStorage.setItem(OPT_IN_KEY, "1");
            return;
        }
        localStorage.removeItem(OPT_IN_KEY);
        clear();
    }

    function save(entry) {
        if (!isEnabled()) {
            return;
        }
        const token = normalizeToken(entry && entry.token);
        if (!token) {
            return;
        }
        const items = list().filter((it) => it.token !== token);
        items.unshift({
            token: token,
            path: entry.path || ("/c/" + token),
            platform: entry.platform || "",
            state: entry.state || "",
            touchedAt: entry.touchedAt || new Date().toISOString()
        });
        persist(items);
    }

    function remove(tokenOrUrl) {
        if (!canUseStorage()) {
            return;
        }
        const token = normalizeToken(tokenOrUrl);
        if (!token) {
            return;
        }
        const items = list().filter((it) => it.token !== token);
        persist(items);
    }

    function clear() {
        if (!canUseStorage()) {
            return;
        }
        localStorage.removeItem(STORAGE_KEY);
    }

    function render(containerId) {
        const container = document.getElementById(containerId);
        if (!container) {
            return;
        }
        if (!isEnabled()) {
            container.innerHTML = '<p class="text-sm text-gray-500">Case vault is off on this browser. Enable "Remember cases on this device" to keep quick links.</p>';
            return;
        }
        const items = list();
        if (items.length === 0) {
            container.innerHTML = '<p class="text-sm text-gray-500">No recent cases stored on this browser.</p>';
            return;
        }

        const html = items.map((it) => {
            const platform = it.platform ? `<span class="text-xs text-gray-500">${escapeHtml(it.platform)}</span>` : "";
            const state = it.state ? `<span class="text-xs text-gray-500">${escapeHtml(it.state)}</span>` : "";
            return [
                '<div class="flex items-center justify-between gap-3 p-3 border border-gray-200 rounded-lg bg-white">',
                '<div class="min-w-0">',
                `<div class="font-mono text-xs text-gray-800 truncate">${escapeHtml(it.token)}</div>`,
                `<div class="mt-1 flex items-center gap-2">${platform}${state}</div>`,
                "</div>",
                '<div class="flex items-center gap-2 shrink-0">',
                `<a class="cb-btn cb-btn-secondary text-xs px-3 py-2" href="${escapeHtml(it.path)}">Open</a>`,
                `<button type="button" class="cb-btn cb-btn-danger text-xs px-3 py-2" onclick="window.cbCaseVault.remove('${escapeJs(it.token)}'); window.cbCaseVault.render('${escapeJs(containerId)}')">Remove</button>`,
                "</div>",
                "</div>"
            ].join("");
        }).join("");

        container.innerHTML = html;
    }

    function escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }

    function escapeJs(value) {
        return String(value).replaceAll("\\", "\\\\").replaceAll("'", "\\'");
    }

    window.cbCaseVault = {
        list,
        save,
        remove,
        clear,
        render,
        normalizeToken,
        isEnabled,
        setEnabled,
        ttlDays: TTL_DAYS
    };
})();
