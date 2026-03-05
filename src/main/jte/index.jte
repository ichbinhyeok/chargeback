@param String message
@param String error

@template.layout(title = "Chargeback Evidence Builder", content = @`
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
            Win More Disputes
        </div>
        
        <h1 class="text-4xl md:text-5xl lg:text-6xl font-extrabold text-neutral-800 tracking-tight leading-tight mb-6">
            Stop Auto-Losing Disputes from <span class="text-transparent bg-clip-text bg-gradient-to-r from-primary-600 to-indigo-600">Formatting Mistakes</span>
        </h1>
        
        <p class="text-lg md:text-xl text-gray-600 mb-10 max-w-2xl leading-relaxed">
            Upload your raw evidence files. We validate them against Stripe and Shopify rules, then help you auto-fix supported issues and export a submission pack.
        </p>
        
        <div class="flex flex-col sm:flex-row gap-4">
            <a href="/new" class="cb-btn cb-btn-primary px-8 py-4 text-base bg-primary-600 hover:bg-primary-700 w-full sm:w-auto">
                Start New Case
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3"></path></svg>
            </a>
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
            <div id="recent-cases-list" class="space-y-2"></div>
        </section>
    </div>
    
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mt-8 border-t border-gray-100 pt-12">
        <div>
            <div class="w-10 h-10 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center mb-4">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
            </div>
            <h3 class="font-semibold text-neutral-800 mb-2">Automated Rule Checks</h3>
            <p class="text-sm text-gray-500 leading-relaxed">Instantly check if your evidence files violate major payment gateway requirements before you submit.</p>
        </div>
        <div>
            <div class="w-10 h-10 rounded-lg bg-green-50 text-green-600 flex items-center justify-center mb-4">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 002 2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path></svg>
            </div>
            <h3 class="font-semibold text-neutral-800 mb-2">Smart Auto-Fix</h3>
            <p class="text-sm text-gray-500 leading-relaxed">Merge duplicate files per evidence type and compress oversized Shopify images.</p>
        </div>
        <div>
            <div class="w-10 h-10 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center mb-4">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path></svg>
            </div>
            <h3 class="font-semibold text-neutral-800 mb-2">One-Click Export</h3>
            <p class="text-sm text-gray-500 leading-relaxed">Download a structured, correctly named zip package ready to be uploaded to Stripe or Shopify.</p>
        </div>
    </div>

    <script>
        document.addEventListener('DOMContentLoaded', function () {
            if (window.cbCaseVault) {
                window.cbCaseVault.render('recent-cases-list');
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
