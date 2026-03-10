@param gg.jte.Content content
@param String title = "Chargeback Evidence Preflight"
@param String metaDescription = "Free preflight checks and beta export workflows for Stripe and Shopify dispute evidence."
@param String canonicalUrl = ""
@param String robotsMeta = "index,follow"
@param String caseTokenForVault = ""
@param String casePlatformForVault = ""
@param String caseStateForVault = ""
@param String caseStage = ""

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title}</title>
    <meta name="description" content="${metaDescription}">
    <meta name="robots" content="${robotsMeta}">
    @if(canonicalUrl != null && !canonicalUrl.isBlank())
        <link rel="canonical" href="${canonicalUrl}">
    @endif
    <meta property="og:title" content="${title}">
    <meta property="og:description" content="${metaDescription}">
    <meta property="og:type" content="website">
    @if(canonicalUrl != null && !canonicalUrl.isBlank())
        <meta property="og:url" content="${canonicalUrl}">
    @endif
    <link rel="icon" type="image/svg+xml" href="/favicon.svg">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="/css/app.css">
    <script src="/js/case-vault.js" defer></script>
    <script src="/js/seo-tracker.js" defer></script>
    <style>
        body { background-color: #F6F9FC; color: #1A1F36; font-family: 'Inter', sans-serif; }
    </style>
</head>
<body class="antialiased min-h-screen flex flex-col">
    <!-- header -->
    <header class="bg-white border-b border-gray-100 shadow-sm sticky top-0 z-10 w-full">
        <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
            <div class="flex items-center gap-2">
                <a href="/" class="font-bold text-xl tracking-tight text-neutral-800 flex items-center gap-2 hover:text-primary-600 transition-colors">
                    <svg class="w-6 h-6 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.957 11.957 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path></svg>
                    Chargeback Preflight
                </a>
            </div>
            <nav class="flex gap-6 items-center">
                <a href="/new" class="text-sm font-medium text-gray-500 hover:text-gray-900 transition-colors">New Case</a>
                <a href="/guides" class="text-sm font-medium text-gray-500 hover:text-gray-900 transition-colors">Help Center</a>
            </nav>
        </div>
    </header>

    <!-- content -->
    <main class="flex-1 w-full max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10 fade-in">
        @if(caseTokenForVault != null && !caseTokenForVault.isBlank() && caseStage != null && !caseStage.isBlank())
            <section class="mb-8 rounded-2xl border border-slate-200 bg-white/95 p-5 shadow-sm">
                <div class="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
                    <div>
                        <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Case workflow</div>
                        <h2 class="mt-1 text-lg font-bold text-slate-900">Keep the next click obvious.</h2>
                    </div>
                    <p class="text-sm text-slate-500">You can move between steps anytime. Export stays gated until review is clean.</p>
                </div>
                <div class="mt-4 grid gap-3 md:grid-cols-4">
                    <a href="/c/${caseTokenForVault}" class="block rounded-xl border p-4 transition-colors ${"overview".equals(caseStage) ? "border-primary-300 bg-primary-50 text-primary-950 shadow-sm" : "border-slate-200 bg-white text-slate-700 hover:border-slate-300"}">
                        <div class="text-xs font-semibold uppercase tracking-wide ${"overview".equals(caseStage) ? "text-primary-700" : "text-slate-500"}">Step 1</div>
                        <div class="mt-1 text-sm font-bold">Overview</div>
                        <p class="mt-1 text-xs leading-5 ${"overview".equals(caseStage) ? "text-primary-900" : "text-slate-500"}">Check the case setup, readiness, and first action.</p>
                    </a>
                    <a href="/c/${caseTokenForVault}/upload" class="block rounded-xl border p-4 transition-colors ${"upload".equals(caseStage) ? "border-primary-300 bg-primary-50 text-primary-950 shadow-sm" : "border-slate-200 bg-white text-slate-700 hover:border-slate-300"}">
                        <div class="text-xs font-semibold uppercase tracking-wide ${"upload".equals(caseStage) ? "text-primary-700" : "text-slate-500"}">Step 2</div>
                        <div class="mt-1 text-sm font-bold">Upload files</div>
                        <p class="mt-1 text-xs leading-5 ${"upload".equals(caseStage) ? "text-primary-900" : "text-slate-500"}">Map each file once, then upload and auto-run checks.</p>
                    </a>
                    <a href="/c/${caseTokenForVault}/validate" class="block rounded-xl border p-4 transition-colors ${"review".equals(caseStage) ? "border-primary-300 bg-primary-50 text-primary-950 shadow-sm" : "border-slate-200 bg-white text-slate-700 hover:border-slate-300"}">
                        <div class="text-xs font-semibold uppercase tracking-wide ${"review".equals(caseStage) ? "text-primary-700" : "text-slate-500"}">Step 3</div>
                        <div class="mt-1 text-sm font-bold">Review &amp; fix</div>
                        <p class="mt-1 text-xs leading-5 ${"review".equals(caseStage) ? "text-primary-900" : "text-slate-500"}">Resolve blockers, confirm missing evidence, and draft the response.</p>
                    </a>
                    <a href="/c/${caseTokenForVault}/export" class="block rounded-xl border p-4 transition-colors ${"export".equals(caseStage) ? "border-primary-300 bg-primary-50 text-primary-950 shadow-sm" : "border-slate-200 bg-white text-slate-700 hover:border-slate-300"}">
                        <div class="text-xs font-semibold uppercase tracking-wide ${"export".equals(caseStage) ? "text-primary-700" : "text-slate-500"}">Step 4</div>
                        <div class="mt-1 text-sm font-bold">Export</div>
                        <p class="mt-1 text-xs leading-5 ${"export".equals(caseStage) ? "text-primary-900" : "text-slate-500"}">Download the final pack only after review is ready.</p>
                    </a>
                </div>
            </section>
        @endif
        ${content}
    </main>

    <!-- footer -->
    <footer class="mt-auto py-8">
        <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between text-center sm:text-left text-sm text-gray-400">
            <p>&copy; 2026 Chargeback Evidence Preflight. Beta software. Not financial or legal advice.</p>
            <div class="flex gap-4 mt-4 sm:mt-0 items-center justify-center">
                <a href="mailto:shinhyeok22@gmail.com" class="hover:text-gray-600 transition-colors">shinhyeok22@gmail.com</a>
                <a href="/terms" class="hover:text-gray-600 transition-colors">Terms</a>
                <a href="/privacy" class="hover:text-gray-600 transition-colors">Privacy</a>
            </div>
        </div>
    </footer>
    <style>
        .fade-in { animation: fadeIn 0.4s ease-out forwards; opacity: 0; }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
        /* Common card styling */
        .cb-card { background: white; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05); padding: 24px; border: 1px solid #f3f4f6; transition: all 0.2s; }
        .cb-input { width: 100%; border-radius: 8px; border: 1px solid #e5e7eb; padding: 10px 14px; font-size: 14px; color: #1f2937; outline: none; transition: border-color 0.15s, box-shadow 0.15s; }
        .cb-input:focus { border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1); }
        .cb-btn { display: inline-flex; align-items: center; justify-content: center; gap: 8px; font-weight: 500; font-size: 14px; border-radius: 8px; padding: 10px 18px; transition: all 0.2s; cursor: pointer; text-decoration: none; border: 1px solid transparent; }
        .cb-btn-primary { background-color: #2563eb; color: white; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
        .cb-btn-primary:hover { background-color: #1d4ed8; box-shadow: 0 4px 6px -1px rgba(37,99,235,0.2); transform: translateY(-1px); }
        .cb-btn-secondary { background-color: white; color: #374151; border-color: #d1d5db; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }
        .cb-btn-secondary:hover { background-color: #f9fafb; color: #111827; }
        .cb-btn-danger { background-color: white; color: #dc2626; border-color: #fca5a5; }
        .cb-btn-danger:hover { background-color: #fef2f2; color: #b91c1c; border-color: #f87171; }
        .cb-badge { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 9999px; font-size: 12px; font-weight: 500; }
        .cb-badge-gray { background-color: #f3f4f6; color: #4b5563; }
        .cb-badge-green { background-color: #dcfce7; color: #166534; }
        .cb-badge-red { background-color: #fee2e2; color: #991b1b; }
        .cb-badge-blue { background-color: #dbeafe; color: #1e40af; }
        .cb-badge-amber { background-color: #fef3c7; color: #92400e; }
        .cb-break-anywhere { overflow-wrap: anywhere; word-break: break-word; }
        /* table */
        .cb-table { width: 100%; border-collapse: separate; border-spacing: 0; }
        .cb-table th { background-color: #f9fafb; text-align: left; padding: 12px 16px; font-size: 12px; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: 0.05em; border-bottom: 1px solid #e5e7eb; border-top: 1px solid #e5e7eb; }
        .cb-table th:first-child { border-top-left-radius: 8px; border-left: 1px solid #e5e7eb; }
        .cb-table th:last-child { border-top-right-radius: 8px; border-right: 1px solid #e5e7eb; }
        .cb-table td { padding: 16px; font-size: 14px; border-bottom: 1px solid #e5e7eb; color: #374151; background: white; }
        .cb-table tr:hover td { background-color: #f9fafb; }
    </style>
    @if(caseTokenForVault != null && !caseTokenForVault.isBlank())
        <script>
            document.addEventListener('DOMContentLoaded', function () {
                if (!window.cbCaseVault) {
                    return;
                }
                window.cbCaseVault.save({
                    token: '${caseTokenForVault}',
                    path: '/c/${caseTokenForVault}',
                    platform: '${casePlatformForVault}',
                    state: '${caseStateForVault}',
                    touchedAt: new Date().toISOString()
                });
            });
        </script>
    @endif
</body>
</html>
