@import com.example.demo.dispute.domain.Platform
@import com.example.demo.dispute.domain.ProductScope
@import com.example.demo.dispute.domain.CardNetwork
@import com.example.demo.dispute.service.ReasonCodeChecklistService
@import com.example.demo.dispute.web.CaseUiLabels
@import java.util.List
@param Platform[] platforms
@param ProductScope[] productScopes
@param CardNetwork[] cardNetworks
@param List<ReasonCodeChecklistService.ReasonOption> stripeReasonOptions
@param List<ReasonCodeChecklistService.ReasonOption> shopifyReasonOptions
@param String error
@param String attributionSource
@param String attributionGuide
@param String attributionPlatform
@param String prefillPlatform
@param String prefillReasonCode
@param String routerQuery
@param int retentionDays
@param int retentionDueDateBufferDays
@param String checkoutPriceDisplay

@template.layout(title = "Start Free Evidence Preflight", content = @`
    <div class="max-w-2xl mx-auto">
        <div class="mb-6">
            <a href="/" class="inline-flex items-center text-sm font-medium text-gray-500 hover:text-gray-900 transition-colors">
                <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path></svg>
                Back to Home
            </a>
        </div>

        @if(error != null && !error.isBlank())
            <div class="mb-6 p-4 rounded-lg bg-red-50 text-red-800 border-l-4 border-red-500 flex items-center gap-3">
                <svg class="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                <span class="font-medium">${error}</span>
            </div>
        @endif

        @if("guide_router_nomatch".equals(attributionSource) && routerQuery != null && !routerQuery.isBlank())
            <div class="mb-6 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                <p class="font-semibold mb-1">No exact guide match yet</p>
                <p>Continuing with direct fix flow for: <code>${routerQuery}</code></p>
            </div>
        @endif

        <div class="mb-6 rounded-lg border border-blue-100 bg-blue-50 p-4 text-sm text-blue-900">
            <p class="font-semibold mb-2">Privacy & Trust</p>
            <ul class="list-disc pl-5 space-y-1">
                <li>Case routes use <code>noindex / no-store / no-referrer</code> protections.</li>
                <li>Default retention is <strong>${retentionDays} days</strong> (extended to due date + ${retentionDueDateBufferDays} days buffer when later).</li>
                <li>You can delete your case and files immediately anytime from the dashboard.</li>
            </ul>
        </div>

        <div class="mb-6 rounded-lg border border-green-100 bg-green-50 p-4 text-sm text-green-900">
            <p class="font-semibold mb-1">Pricing</p>
            <p>Preflight is free. Unlock the beta export for <strong>${checkoutPriceDisplay}</strong> only if you want the clean downloads after validation.</p>
        </div>

        <div class="mb-6 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-900">
            <p class="font-semibold mb-2">What happens next</p>
            <ol class="space-y-2 pl-5 list-decimal">
                <li>No files are uploaded on this page. This step only creates the case and loads the right rules.</li>
                <li>On the dashboard, click <strong>Upload Evidence</strong>.</li>
                <li>Choose files, confirm each evidence type in the mapping modal, then click <strong>Upload Files</strong>.</li>
                <li>Validation runs automatically after upload. Beta export only unlocks after validation is fresh and required evidence is complete.</li>
            </ol>
        </div>

        <div class="cb-card">
            <div class="border-b border-gray-100 pb-5 mb-6">
                <h1 class="text-2xl font-bold text-neutral-800">Start Free Evidence Preflight</h1>
                <p class="text-gray-500 text-sm mt-1">Choose the platform rules first, then add only the details that improve the checklist and validation path.</p>
                <div class="mt-4 grid gap-3 md:grid-cols-3">
                    <div class="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-900">
                        <div class="font-semibold">1. Required now</div>
                        <p class="mt-1 text-slate-700">Pick the platform and dispute flow.</p>
                    </div>
                    <div class="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-900">
                        <div class="font-semibold">2. Recommended now</div>
                        <p class="mt-1 text-slate-700">Add the reason preset if you already know it.</p>
                    </div>
                    <div class="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-900">
                        <div class="font-semibold">3. Optional details</div>
                        <p class="mt-1 text-slate-700">Card network and due date only tighten the rules.</p>
                    </div>
                </div>
            </div>

            <form id="new-case-form" method="post" action="/new" class="space-y-6">
                <input type="hidden" name="src" value="${attributionSource != null ? attributionSource : ""}">
                <input type="hidden" name="guide" value="${attributionGuide != null ? attributionGuide : ""}">
                <input type="hidden" name="sourcePlatform" value="${attributionPlatform != null ? attributionPlatform : ""}">
                <input type="hidden" name="query" value="${routerQuery != null ? routerQuery : ""}">
                <section class="rounded-xl border border-slate-200 bg-slate-50/80 p-4">
                    <div class="mb-4">
                        <h2 class="text-sm font-semibold uppercase tracking-wide text-slate-700">Required now</h2>
                        <p class="mt-1 text-sm text-slate-600">This decides which validation rules, evidence checklist, and export gates the case uses.</p>
                    </div>
                    <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
                        <div class="space-y-1.5">
                            <label for="platform" class="block text-sm font-semibold text-gray-700">Platform</label>
                            <select id="platform" name="platform" required class="cb-input">
                                <option value="" disabled selected="${prefillPlatform == null}">Select platform</option>
                                @for(Platform platform : platforms)
                                    <option value="${platform.name()}" selected="${prefillPlatform != null && prefillPlatform.equals(platform.name())}">${CaseUiLabels.platformLabel(platform)}</option>
                                @endfor
                            </select>
                        </div>

                        <div class="space-y-1.5">
                            <label for="productScope" class="block text-sm font-semibold text-gray-700">Dispute flow</label>
                            <select id="productScope" name="productScope" required class="cb-input">
                                <option value="" disabled selected>Select dispute flow</option>
                                @for(ProductScope scope : productScopes)
                                    <option value="${scope.name()}" data-platform="${scope.platform().name()}" data-description="${CaseUiLabels.productScopeDescription(scope)}">${CaseUiLabels.productScopeLabel(scope)}</option>
                                @endfor
                            </select>
                            <p id="scopeHint" class="text-xs text-slate-500">Select a platform first to narrow the flow and rule set.</p>
                        </div>
                    </div>
                </section>

                <section class="rounded-xl border border-emerald-200 bg-emerald-50/70 p-4">
                    <div class="mb-4">
                        <h2 class="text-sm font-semibold uppercase tracking-wide text-emerald-800">Recommended now</h2>
                        <p class="mt-1 text-sm text-emerald-900">If you already know the platform reason, load it now so the checklist starts closer to the real dispute.</p>
                    </div>
                    <div class="space-y-1.5">
                        <label for="reasonPreset" class="block text-sm font-semibold text-gray-700">Reason Code Preset <span class="ml-1 font-normal text-emerald-700">(Recommended)</span></label>
                        <select id="reasonPreset" class="cb-input bg-gray-50 text-gray-600" data-prefill-code="${prefillReasonCode != null ? prefillReasonCode : ""}">
                            <option value="">Not set</option>
                        </select>
                        <input id="reasonCustom" type="text" placeholder="Custom reason code (e.g. 13.1, item_not_received)" class="cb-input bg-gray-50 text-gray-600 hidden mt-2">
                        <input id="reasonCode" type="hidden" name="reasonCode">
                        <p id="reasonHint" class="text-xs text-emerald-800">Select a platform to load recommended reason presets.</p>
                    </div>
                </section>

                <details class="rounded-xl border border-gray-200 bg-white">
                    <summary class="cursor-pointer list-none px-4 py-3 text-sm font-semibold text-gray-700">
                        Optional case details
                        <span class="ml-2 font-normal text-gray-500">Add these only if you already have them.</span>
                    </summary>
                    <div class="grid grid-cols-1 gap-6 border-t border-gray-100 px-4 py-4 sm:grid-cols-2">
                        <div class="space-y-1.5">
                            <label for="cardNetwork" class="block text-sm font-semibold text-gray-700">Card network</label>
                            <select id="cardNetwork" name="cardNetwork" class="cb-input bg-gray-50">
                                <option value="">Not set</option>
                                @for(CardNetwork network : cardNetworks)
                                    <option value="${network.name()}">${CaseUiLabels.cardNetworkLabel(network)}</option>
                                @endfor
                            </select>
                            <p class="text-xs text-gray-500">Useful when network-specific evidence rules apply.</p>
                        </div>

                        <div class="space-y-1.5">
                            <label for="dueAt" class="block text-sm font-semibold text-gray-700">Due date</label>
                            <input id="dueAt" type="date" name="dueAt" class="cb-input bg-gray-50 text-gray-600">
                            <p class="text-xs text-gray-500">Used for retention timing and deadline visibility.</p>
                        </div>
                    </div>
                </details>

                <div class="pt-4 border-t border-gray-100 flex justify-end">
                    <button type="submit" class="cb-btn cb-btn-primary w-full sm:w-auto px-8">
                        Continue to Free Preflight
                    </button>
                </div>
            </form>
        </div>

        <details class="mb-6 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <summary class="cursor-pointer list-none">
                <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div>
                        <p class="text-sm font-semibold uppercase tracking-wide text-slate-700">Start From The Platform Error</p>
                        <h2 class="mt-1 text-xl font-bold text-neutral-900">Load the closest fix flow before case setup</h2>
                        <p class="mt-2 max-w-2xl text-sm leading-6 text-gray-600">
                            Optional shortcut for users who already have the exact Stripe or Shopify rejection message.
                        </p>
                    </div>
                    @if("guide".equals(attributionSource) && attributionPlatform != null && attributionGuide != null)
                        <div class="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-900 lg:max-w-sm">
                            <div class="font-semibold">Loaded from error guide</div>
                            <div class="mt-1"><code>${attributionPlatform}</code> / <code>${attributionGuide}</code></div>
                        </div>
                    @endif
                </div>
            </summary>

            <p class="mt-4 max-w-2xl text-sm leading-6 text-gray-600">
                Most users arrive after Stripe or Shopify already rejected a file. Start with the exact error if you have it.
            </p>
            <form id="error-first-form" method="get" action="/guides/router" class="mt-4 grid gap-3 md:grid-cols-[180px,220px,1fr,auto]">
                <select id="errorPlatform" name="platform" class="cb-input">
                    <option value="">Auto-detect platform</option>
                    <option value="stripe">Stripe</option>
                    <option value="shopify">Shopify</option>
                </select>
                <select id="errorPreset" class="cb-input">
                    <option value="">Choose common error</option>
                </select>
                <input
                    id="errorQuery"
                    type="text"
                    name="q"
                    class="cb-input"
                    placeholder="Or paste the exact error words"
                    value="${routerQuery != null ? routerQuery : ""}"
                >
                <button type="submit" class="cb-btn cb-btn-primary">
                    Load Error-Specific Flow
                </button>
            </form>

            <p class="mt-3 text-xs text-gray-500">
                If you do not have an exact error, continue with case setup above and use free validation to surface blockers.
            </p>
        </details>
    </div>

    <div id="reason-options-store" class="hidden">
        @for(var option : stripeReasonOptions)
            <div data-platform="STRIPE" data-code="${option.code()}" data-label="${option.label()}" data-hint="${option.hint()}"></div>
        @endfor
        @for(var option : shopifyReasonOptions)
            <div data-platform="SHOPIFY" data-code="${option.code()}" data-label="${option.label()}" data-hint="${option.hint()}"></div>
        @endfor
    </div>

    <div id="error-preset-store" class="hidden">
        <div data-platform="stripe" data-slug="invalid-file-format-pdf-jpg-png-only" data-label="Invalid file format"></div>
        <div data-platform="stripe" data-slug="upload-failed-no-external-links" data-label="No external links"></div>
        <div data-platform="stripe" data-slug="duplicate-evidence-type-file-error" data-label="Duplicate evidence type"></div>
        <div data-platform="stripe" data-slug="evidence-file-size-limit-4-5mb" data-label="Evidence size limit"></div>
        <div data-platform="stripe" data-slug="total-pages-over-limit" data-label="Total pages over limit"></div>
        <div data-platform="shopify" data-slug="pdf-a-format-required-error" data-label="PDF/A required"></div>
        <div data-platform="shopify" data-slug="evidence-file-too-large-2mb" data-label="File too large (2MB)"></div>
        <div data-platform="shopify" data-slug="pdf-portfolio-not-accepted" data-label="PDF portfolio not accepted"></div>
        <div data-platform="shopify" data-slug="external-links-not-allowed-error" data-label="External links not allowed"></div>
        <div data-platform="shopify" data-slug="duplicate-evidence-type-file-error" data-label="Duplicate evidence type"></div>
    </div>

    <script>
        const newCaseForm = document.getElementById('new-case-form');
        const platformSelect = document.getElementById('platform');
        const productScopeSelect = document.getElementById('productScope');
        const reasonPresetSelect = document.getElementById('reasonPreset');
        const reasonCustomInput = document.getElementById('reasonCustom');
        const reasonCodeInput = document.getElementById('reasonCode');
        const reasonHint = document.getElementById('reasonHint');
        const scopeHint = document.getElementById('scopeHint');
        const reasonOptionNodes = document.querySelectorAll('#reason-options-store [data-platform]');
        const errorFirstForm = document.getElementById('error-first-form');
        const errorPlatformSelect = document.getElementById('errorPlatform');
        const errorPresetSelect = document.getElementById('errorPreset');
        const errorQueryInput = document.getElementById('errorQuery');
        const errorPresetNodes = document.querySelectorAll('#error-preset-store [data-platform]');
        const sourcePlatformInput = document.querySelector('input[name="sourcePlatform"]');
        const prefillPlatform = ((sourcePlatformInput && sourcePlatformInput.value) ? sourcePlatformInput.value : '').trim().toUpperCase();
        const prefillReasonCode = (reasonPresetSelect.dataset.prefillCode || '').trim();
        const prefillErrorGuide = ('${attributionSource != null ? attributionSource : ""}' === 'guide')
            ? ('${attributionGuide != null ? attributionGuide : ""}')
            : '';
        let reasonPrefillApplied = false;

        const reasonOptionsByPlatform = {};
        const errorPresetsByPlatform = {};
        reasonOptionNodes.forEach((node) => {
            const platform = node.dataset.platform;
            const entry = {
                code: node.dataset.code || '',
                label: node.dataset.label || node.dataset.code || '',
                hint: node.dataset.hint || ''
            };
            if (!reasonOptionsByPlatform[platform]) {
                reasonOptionsByPlatform[platform] = [];
            }
            reasonOptionsByPlatform[platform].push(entry);
        });
        errorPresetNodes.forEach((node) => {
            const platform = node.dataset.platform;
            const entry = {
                slug: node.dataset.slug || '',
                label: node.dataset.label || node.dataset.slug || ''
            };
            if (!errorPresetsByPlatform[platform]) {
                errorPresetsByPlatform[platform] = [];
            }
            errorPresetsByPlatform[platform].push(entry);
        });

        function refreshScopeOptions() {
            const selectedPlatform = platformSelect.value;
            const options = Array.from(productScopeSelect.options).filter((option) => option.value);

            let firstEnabledValue = '';
            options.forEach((option) => {
                const optionPlatform = option.dataset.platform;
                const enabled = !selectedPlatform || optionPlatform === selectedPlatform;
                option.disabled = !enabled;
                option.hidden = !enabled;
                if (enabled && !firstEnabledValue) {
                    firstEnabledValue = option.value;
                }
            });

            const selectedOption = productScopeSelect.options[productScopeSelect.selectedIndex];
            const selectedIsEnabled = selectedOption && !selectedOption.disabled && selectedOption.value;
            if (!selectedIsEnabled) {
                productScopeSelect.value = firstEnabledValue || '';
            }
            refreshScopeHint();
        }

        function refreshScopeHint() {
            const selectedOption = productScopeSelect.options[productScopeSelect.selectedIndex];
            const selectedDescription = selectedOption ? (selectedOption.dataset.description || '') : '';
            scopeHint.textContent = selectedDescription || 'Select a platform first to narrow the flow and rule set.';
        }

        function refreshReasonPresetOptions() {
            const selectedPlatform = platformSelect.value;
            const currentValue = reasonPresetSelect.value;
            const options = reasonOptionsByPlatform[selectedPlatform] || [];

            reasonPresetSelect.innerHTML = '';
            addReasonOption('', 'Not set', '');
            options.forEach((option) => addReasonOption(option.code, option.label, option.hint));
            addReasonOption('__CUSTOM__', 'Custom input', 'Use this if your reason code is not listed.');

            let targetValue = currentValue;
            if (!reasonPrefillApplied && prefillReasonCode) {
                targetValue = prefillReasonCode;
                reasonPrefillApplied = true;
            }
            const hasTargetValue = Array.from(reasonPresetSelect.options).some((option) => option.value === targetValue);
            reasonPresetSelect.value = hasTargetValue ? targetValue : '';
            refreshReasonInputs();
        }

        function addReasonOption(value, label, hint) {
            const option = document.createElement('option');
            option.value = value;
            option.textContent = label;
            option.dataset.hint = hint || '';
            reasonPresetSelect.appendChild(option);
        }

        function refreshErrorPresetOptions() {
            const selectedPlatform = errorPlatformSelect.value || platformSelect.value.toLowerCase();
            const options = errorPresetsByPlatform[selectedPlatform] || [];
            const currentValue = errorPresetSelect.value;

            errorPresetSelect.innerHTML = '';
            addErrorPresetOption('', 'Choose common error');
            options.forEach((option) => addErrorPresetOption(option.slug, option.label));

            const targetValue = currentValue || prefillErrorGuide;
            const hasTargetValue = Array.from(errorPresetSelect.options).some((option) => option.value === targetValue);
            errorPresetSelect.value = hasTargetValue ? targetValue : '';
        }

        function addErrorPresetOption(value, label) {
            const option = document.createElement('option');
            option.value = value;
            option.textContent = label;
            errorPresetSelect.appendChild(option);
        }

        function refreshReasonInputs() {
            const selectedValue = reasonPresetSelect.value;
            const selectedOption = reasonPresetSelect.options[reasonPresetSelect.selectedIndex];
            const selectedHint = selectedOption ? (selectedOption.dataset.hint || '') : '';

            if (selectedValue === '__CUSTOM__') {
                reasonCustomInput.classList.remove('hidden');
                reasonHint.textContent = selectedHint || 'Enter a custom reason code.';
            } else {
                reasonCustomInput.classList.add('hidden');
                reasonHint.textContent = selectedHint || 'Select a reason preset or leave unset.';
            }
            syncReasonCodeValue();
        }

        function syncReasonCodeValue() {
            if (reasonPresetSelect.value === '__CUSTOM__') {
                reasonCodeInput.value = reasonCustomInput.value.trim();
                return;
            }
            reasonCodeInput.value = reasonPresetSelect.value || '';
        }

        platformSelect.addEventListener('change', () => {
            refreshScopeOptions();
            refreshReasonPresetOptions();
            if (!errorPlatformSelect.value) {
                errorPlatformSelect.value = platformSelect.value.toLowerCase();
            }
            refreshErrorPresetOptions();
        });

        productScopeSelect.addEventListener('change', refreshScopeHint);
        reasonPresetSelect.addEventListener('change', refreshReasonInputs);
        reasonCustomInput.addEventListener('input', syncReasonCodeValue);
        newCaseForm.addEventListener('submit', syncReasonCodeValue);
        errorPlatformSelect.addEventListener('change', () => {
            const selectedPlatform = errorPlatformSelect.value;
            if (selectedPlatform && platformSelect.value !== selectedPlatform.toUpperCase()) {
                platformSelect.value = selectedPlatform.toUpperCase();
                refreshScopeOptions();
                refreshReasonPresetOptions();
            }
            refreshErrorPresetOptions();
        });
        errorFirstForm.addEventListener('submit', (event) => {
            const selectedPlatform = errorPlatformSelect.value || platformSelect.value.toLowerCase();
            const selectedPreset = errorPresetSelect.value;
            const enteredQuery = errorQueryInput.value.trim();

            if (enteredQuery) {
                if (!errorPlatformSelect.value && selectedPlatform) {
                    errorPlatformSelect.value = selectedPlatform;
                }
                return;
            }

            if (selectedPreset && selectedPlatform) {
                event.preventDefault();
                window.location.href = '/new?src=guide&platform='
                    + encodeURIComponent(selectedPlatform)
                    + '&guide='
                    + encodeURIComponent(selectedPreset);
                return;
            }

            event.preventDefault();
            errorQueryInput.focus();
        });

        if (prefillPlatform && Array.from(platformSelect.options).some((option) => option.value === prefillPlatform)) {
            platformSelect.value = prefillPlatform;
        }
        if (prefillPlatform) {
            errorPlatformSelect.value = prefillPlatform.toLowerCase();
        }
        refreshScopeOptions();
        refreshReasonPresetOptions();
        refreshErrorPresetOptions();
    </script>
`)
