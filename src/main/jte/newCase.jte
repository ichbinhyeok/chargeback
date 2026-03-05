@import com.example.demo.dispute.domain.Platform
@import com.example.demo.dispute.domain.ProductScope
@import com.example.demo.dispute.domain.CardNetwork
@param Platform[] platforms
@param ProductScope[] productScopes
@param CardNetwork[] cardNetworks
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

            <form method="post" action="/new" class="space-y-6">
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
                                <option value="${scope.name()}">${scope.name()}</option>
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
                        <label for="reasonCode" class="block text-sm font-semibold text-gray-700">Reason Code <span class="text-gray-400 font-normal ml-1">(Optional)</span></label>
                        <input id="reasonCode" type="text" name="reasonCode" placeholder="e.g. fraudulent, product_not_received" class="cb-input bg-gray-50 text-gray-600">
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
`)
