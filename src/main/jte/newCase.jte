@import com.example.demo.dispute.domain.Platform
@import com.example.demo.dispute.domain.ProductScope
@import com.example.demo.dispute.domain.CardNetwork
@import com.example.demo.dispute.service.ReasonCodeChecklistService
@import java.util.List
@param Platform[] platforms
@param ProductScope[] productScopes
@param CardNetwork[] cardNetworks
@param List<ReasonCodeChecklistService.ReasonOption> stripeReasonOptions
@param List<ReasonCodeChecklistService.ReasonOption> shopifyReasonOptions
@param String error

@template.layout(title = "Create New Case", content = @`
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

        <div class="cb-card">
            <div class="border-b border-gray-100 pb-5 mb-6">
                <h1 class="text-2xl font-bold text-neutral-800">Create New Dispute Case</h1>
                <p class="text-gray-500 text-sm mt-1">Select the platform and dispute scope to set up validation rules.</p>
            </div>

            <form id="new-case-form" method="post" action="/new" class="space-y-6">
                <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
                    <div class="space-y-1.5">
                        <label for="platform" class="block text-sm font-semibold text-gray-700">Platform</label>
                        <select id="platform" name="platform" required class="cb-input">
                            <option value="" disabled selected>Select Platform</option>
                            @for(Platform platform : platforms)
                                <option value="${platform.name()}">${platform.name()}</option>
                            @endfor
                        </select>
                    </div>

                    <div class="space-y-1.5">
                        <label for="productScope" class="block text-sm font-semibold text-gray-700">Product Scope</label>
                        <select id="productScope" name="productScope" required class="cb-input">
                            <option value="" disabled selected>Select Scope</option>
                            @for(ProductScope scope : productScopes)
                                <option value="${scope.name()}" data-platform="${scope.platform().name()}">${scope.name()}</option>
                            @endfor
                        </select>
                    </div>

                    <div class="space-y-1.5">
                        <label for="cardNetwork" class="block text-sm font-semibold text-gray-700">Card Network <span class="text-gray-400 font-normal ml-1">(Optional)</span></label>
                        <select id="cardNetwork" name="cardNetwork" class="cb-input bg-gray-50">
                            <option value="">Not set</option>
                            @for(CardNetwork network : cardNetworks)
                                <option value="${network.name()}">${network.name()}</option>
                            @endfor
                        </select>
                        <p class="text-xs text-gray-400">Can affect specific evidence requirements.</p>
                    </div>

                    <div class="space-y-1.5">
                        <label for="dueAt" class="block text-sm font-semibold text-gray-700">Due Date <span class="text-gray-400 font-normal ml-1">(Optional)</span></label>
                        <input id="dueAt" type="date" name="dueAt" class="cb-input bg-gray-50 text-gray-600">
                    </div>
                    
                    <div class="sm:col-span-2 space-y-1.5">
                        <label for="reasonPreset" class="block text-sm font-semibold text-gray-700">Reason Code Preset <span class="text-gray-400 font-normal ml-1">(Optional)</span></label>
                        <select id="reasonPreset" class="cb-input bg-gray-50 text-gray-600">
                            <option value="">Not set</option>
                        </select>
                        <input id="reasonCustom" type="text" placeholder="Custom reason code (e.g. 13.1, item_not_received)" class="cb-input bg-gray-50 text-gray-600 hidden mt-2">
                        <input id="reasonCode" type="hidden" name="reasonCode">
                        <p id="reasonHint" class="text-xs text-gray-400">Select a platform to load recommended reason presets.</p>
                    </div>
                </div>

                <div class="pt-4 border-t border-gray-100 flex justify-end">
                    <button type="submit" class="cb-btn cb-btn-primary w-full sm:w-auto px-8">
                        Continue to Case Options
                    </button>
                </div>
            </form>
        </div>
    </div>

    <div id="reason-options-store" class="hidden">
        @for(var option : stripeReasonOptions)
            <div data-platform="STRIPE" data-code="${option.code()}" data-label="${option.label()}" data-hint="${option.hint()}"></div>
        @endfor
        @for(var option : shopifyReasonOptions)
            <div data-platform="SHOPIFY" data-code="${option.code()}" data-label="${option.label()}" data-hint="${option.hint()}"></div>
        @endfor
    </div>

    <script>
        const newCaseForm = document.getElementById('new-case-form');
        const platformSelect = document.getElementById('platform');
        const productScopeSelect = document.getElementById('productScope');
        const reasonPresetSelect = document.getElementById('reasonPreset');
        const reasonCustomInput = document.getElementById('reasonCustom');
        const reasonCodeInput = document.getElementById('reasonCode');
        const reasonHint = document.getElementById('reasonHint');
        const reasonOptionNodes = document.querySelectorAll('#reason-options-store [data-platform]');

        const reasonOptionsByPlatform = {};
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
        }

        function refreshReasonPresetOptions() {
            const selectedPlatform = platformSelect.value;
            const currentValue = reasonPresetSelect.value;
            const options = reasonOptionsByPlatform[selectedPlatform] || [];

            reasonPresetSelect.innerHTML = '';
            addReasonOption('', 'Not set', '');
            options.forEach((option) => addReasonOption(option.code, option.label, option.hint));
            addReasonOption('__CUSTOM__', 'Custom input', 'Use this if your reason code is not listed.');

            const hasCurrentValue = Array.from(reasonPresetSelect.options).some((option) => option.value === currentValue);
            reasonPresetSelect.value = hasCurrentValue ? currentValue : '';
            refreshReasonInputs();
        }

        function addReasonOption(value, label, hint) {
            const option = document.createElement('option');
            option.value = value;
            option.textContent = label;
            option.dataset.hint = hint || '';
            reasonPresetSelect.appendChild(option);
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
        });

        reasonPresetSelect.addEventListener('change', refreshReasonInputs);
        reasonCustomInput.addEventListener('input', syncReasonCodeValue);
        newCaseForm.addEventListener('submit', syncReasonCodeValue);

        refreshScopeOptions();
        refreshReasonPresetOptions();
    </script>
`)
