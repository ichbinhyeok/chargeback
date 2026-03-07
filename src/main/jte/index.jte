@param String message
@param String error
@param String checkoutPriceDisplay

@template.layout(
    title = "Upload-Ready Dispute Evidence Packs",
    metaDescription = "Validate Stripe and Shopify evidence for free, auto-fix supported issues, and unlock an upload-ready dispute evidence pack when it is ready.",
    content = @`
    @if(message != null && !message.isBlank())
        <div class="mb-6 p-4 rounded-lg bg-green-50 text-green-800 border-l-4 border-green-500 flex items-center gap-3">
            <svg class="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
            <span class="font-medium">${message}</span>
        </div>
    @endif
    @if(error != null && !error.isBlank())
        <div class="mb-6 p-4 rounded-lg bg-red-50 text-red-800 border-l-4 border-red-500 flex items-center gap-3">
            <svg class="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
            <span class="font-medium">${error}</span>
        </div>
    @endif

    <div class="py-12 md:py-20 max-w-3xl">
        <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-primary-50 text-primary-700 text-xs font-semibold uppercase tracking-wide mb-6">
            <span class="relative flex h-2 w-2">
              <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary-400 opacity-75"></span>
              <span class="relative inline-flex rounded-full h-2 w-2 bg-primary-500"></span>
            </span>
            Upload-Ready Evidence Packs
        </div>
        
        <h1 class="text-4xl md:text-5xl lg:text-6xl font-extrabold text-neutral-800 tracking-tight leading-tight mb-6">
            Turn raw dispute files into an <span class="text-transparent bg-clip-text bg-gradient-to-r from-primary-600 to-indigo-600">upload-ready evidence pack</span>
        </h1>
        
        <p class="text-lg md:text-xl text-gray-600 mb-10 max-w-2xl leading-relaxed">
            Validate Stripe and Shopify evidence for free, auto-fix supported issues, and unlock the ZIP, summary PDF, and explanation draft merchants actually submit.
        </p>

        <div class="mb-8 rounded-2xl border border-slate-200 bg-white/90 p-5 shadow-sm">
            <div class="flex flex-col gap-4 lg:flex-row lg:items-end">
                <div class="flex-1">
                    <div class="text-sm font-semibold text-gray-900">Start From The Platform Error</div>
                    <p class="mt-1 text-sm text-gray-600">Paste the exact Stripe or Shopify upload error and jump into the closest fix flow first.</p>
                </div>
            </div>
            <form method="get" action="/guides/router" class="mt-4 grid gap-3 md:grid-cols-[180px,1fr,auto]">
                <select name="platform" class="cb-input">
                    <option value="">Auto-detect platform</option>
                    <option value="stripe">Stripe</option>
                    <option value="shopify">Shopify</option>
                </select>
                <input
                    type="text"
                    name="q"
                    class="cb-input"
                    placeholder="e.g. pdf/a format required, duplicate evidence type file, no external links"
                    required
                >
                <button type="submit" class="cb-btn cb-btn-primary px-6">
                    Find My Fix Flow
                </button>
            </form>
        </div>

        <div class="flex flex-col sm:flex-row gap-4">
            <a href="/new" class="cb-btn cb-btn-secondary px-8 py-4 text-base w-full sm:w-auto justify-center">
                Start Free Validation
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3"></path></svg>
            </a>
        </div>

        <div class="mt-6 flex flex-wrap gap-3 text-sm">
            <span class="inline-flex items-center rounded-full border border-blue-100 bg-blue-50 px-3 py-1 font-medium text-blue-800">Validation is free</span>
            <span class="inline-flex items-center rounded-full border border-green-100 bg-green-50 px-3 py-1 font-medium text-green-800">${checkoutPriceDisplay} to unlock downloads</span>
            <span class="inline-flex items-center rounded-full border border-gray-200 bg-white px-3 py-1 font-medium text-gray-700">ZIP + PDF + TXT outputs</span>
        </div>

        <div class="mt-8 rounded-2xl border border-slate-200 bg-white/90 p-5 shadow-sm">
            <div class="text-sm font-semibold text-gray-900">Already proven on real failed uploads</div>
            <p class="mt-1 text-sm text-gray-600">The current auto-fix flow already cleared these live beta scenarios end-to-end.</p>
            <div class="mt-4 flex flex-wrap gap-2 text-sm">
                <span class="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 font-medium text-slate-800">Shopify PDF/A required</span>
                <span class="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 font-medium text-slate-800">Shopify PDF portfolio</span>
                <span class="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 font-medium text-slate-800">Shopify 2MB receipt photo</span>
                <span class="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 font-medium text-slate-800">Shopify external-link PDF</span>
                <span class="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 font-medium text-slate-800">Stripe Mastercard 19-page packet</span>
                <span class="inline-flex items-center rounded-full border border-slate-200 bg-slate-50 px-3 py-1 font-medium text-slate-800">Scanner TIFF to JPEG</span>
            </div>
        </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        <section class="cb-card">
            <h2 class="text-lg font-semibold text-gray-900 mb-2">Resume Existing Case</h2>
            <p class="text-sm text-gray-600 mb-4">Paste a case token or full case URL to continue without login.</p>
            <form method="post" action="/open-case" class="space-y-3">
                <input type="text" name="caseTokenOrUrl" placeholder="case_abcd1234... or https://.../c/case_abcd1234" class="cb-input" required>
                <button type="submit" class="cb-btn cb-btn-primary">Open Case</button>
            </form>
            <div class="mt-4 pt-4 border-t border-gray-100">
                <p class="text-xs text-gray-500 mb-2">Have an access key file?</p>
                <input id="access-key-file" type="file" accept=".txt" class="cb-input text-sm">
            </div>
        </section>

        <section class="cb-card">
            <div class="flex items-center justify-between mb-3">
                <h2 class="text-lg font-semibold text-gray-900">Recent Cases (This Browser)</h2>
                <button type="button" class="cb-btn cb-btn-secondary text-xs px-3 py-2" onclick="window.cbCaseVault.clear(); window.cbCaseVault.render('recent-cases-list')">Clear</button>
            </div>
            <label class="mb-3 flex items-start gap-2 text-sm text-gray-700">
                <input id="remember-cases-toggle" type="checkbox" class="mt-0.5 rounded border-gray-300">
                <span>Remember cases on this device</span>
            </label>
            <p id="remember-cases-status" class="text-xs text-gray-500 mb-3"></p>
            <div id="recent-cases-list" class="space-y-2"></div>
        </section>
    </div>
    
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mt-8 border-t border-gray-100 pt-12">
        <div>
            <div class="w-10 h-10 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center mb-4">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
            </div>
            <h3 class="font-semibold text-neutral-800 mb-2">Free Validation First</h3>
            <p class="text-sm text-gray-500 leading-relaxed">See readiness score, blocked issues, and required evidence gaps before you pay for downloads.</p>
        </div>
        <div>
            <div class="w-10 h-10 rounded-lg bg-green-50 text-green-600 flex items-center justify-center mb-4">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 002 2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path></svg>
            </div>
            <h3 class="font-semibold text-neutral-800 mb-2">Auto-Fix Supported Blockers</h3>
            <p class="text-sm text-gray-500 leading-relaxed">Fix PDF/A failures, flatten portfolio PDFs, compress oversized Shopify photos, remove hidden PDF links, trim duplicate PDF pages, and auto-convert scanner image formats.</p>
        </div>
        <div>
            <div class="w-10 h-10 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center mb-4">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path></svg>
            </div>
            <h3 class="font-semibold text-neutral-800 mb-2">Unlock The Evidence Pack</h3>
            <p class="text-sm text-gray-500 leading-relaxed">Download a merchant-ready submission ZIP, summary PDF, and explanation draft once the pack is ready.</p>
        </div>
    </div>

    <script>
        document.addEventListener('DOMContentLoaded', function () {
            if (window.cbCaseVault) {
                const rememberToggle = document.getElementById('remember-cases-toggle');
                const rememberStatus = document.getElementById('remember-cases-status');
                const refreshVault = function () {
                    const enabled = window.cbCaseVault.isEnabled();
                    if (rememberToggle) {
                        rememberToggle.checked = enabled;
                    }
                    if (rememberStatus) {
                        rememberStatus.textContent = enabled
                            ? 'Stored for up to ' + window.cbCaseVault.ttlDays + ' days in this browser only.'
                            : 'Off by default for privacy. Enable only on a trusted private device.';
                    }
                    window.cbCaseVault.render('recent-cases-list');
                };
                if (rememberToggle) {
                    rememberToggle.addEventListener('change', function () {
                        window.cbCaseVault.setEnabled(rememberToggle.checked);
                        refreshVault();
                    });
                }
                refreshVault();
            } else {
                const rememberStatus = document.getElementById('remember-cases-status');
                if (rememberStatus) {
                    rememberStatus.textContent = 'Case vault is unavailable in this browser.';
                }
            }

            const input = document.getElementById('access-key-file');
            if (!input) {
                return;
            }

            input.addEventListener('change', function () {
                const file = input.files && input.files[0];
                if (!file) {
                    return;
                }
                const reader = new FileReader();
                reader.onload = function () {
                    const token = window.cbCaseVault ? window.cbCaseVault.normalizeToken(reader.result || '') : '';
                    if (!token) {
                        alert('Could not find a valid case token in the selected file.');
                        return;
                    }
                    window.location.href = '/c/' + encodeURIComponent(token);
                };
                reader.readAsText(file);
            });
        });
    </script>
`)
