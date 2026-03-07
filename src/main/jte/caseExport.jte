@import com.example.demo.dispute.persistence.DisputeCase
@import com.example.demo.dispute.api.CaseReportResponse
@import com.example.demo.dispute.domain.EvidenceType
@import com.example.demo.dispute.persistence.PaymentEntity
@import java.util.List
@param DisputeCase disputeCase
@param CaseReportResponse report
@param EvidenceType[] evidenceTypes
@param boolean isPaid
@param PaymentEntity latestPayment
@param String message
@param String error
@param boolean paymentConfigured
@param boolean exportReady
@param String validationFreshness
@param boolean validationFresh
@param List<String> missingRequiredEvidenceTypes
@param String paymentProviderLabel

@template.layout(
    title = "Export | " + report.caseToken(),
    robotsMeta = "noindex,nofollow,noarchive",
    caseTokenForVault = report.caseToken(),
    casePlatformForVault = report.platform().name(),
    caseStateForVault = report.state().name(),
    content = @`
    <!-- Alerts -->
    @if(message != null && !message.isBlank())
        <div class="mb-6 p-4 rounded-lg bg-green-50 text-green-800 border border-green-200 flex items-center gap-3">
            <svg class="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
            <span class="font-medium">${message}</span>
        </div>
    @endif
    @if(error != null && !error.isBlank())
        <div class="mb-6 p-4 rounded-lg bg-red-50 text-red-800 border border-red-200 flex items-center gap-3">
            <svg class="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
            <span class="font-medium">${error}</span>
        </div>
    @endif
    @if(!validationFresh)
        <div class="mb-6 p-4 rounded-lg bg-amber-50 text-amber-800 border border-amber-200 flex items-center gap-3">
            <svg class="w-5 h-5 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M4.93 19h14.14c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.2 16c-.77 1.33.19 3 1.73 3z"></path></svg>
            <span class="font-medium">Validation is stale (${validationFreshness}). Run validation again before payment/export.</span>
        </div>
    @endif

    <!-- Header Section -->
    <div class="mb-8 flex flex-col md:flex-row md:items-start justify-between gap-4">
        <div>
            <div class="flex items-center gap-3 mb-2">
                <h1 class="text-3xl font-bold text-neutral-900 tracking-tight">Case ${report.caseToken()}</h1>
                <span class="cb-badge ${(report.state().name().equals("READY") || report.state().name().equals("PAID") || report.state().name().equals("DOWNLOADED")) ? "cb-badge-green" : (report.state().name().equals("BLOCKED") ? "cb-badge-red" : "cb-badge-blue")}">
                    ${report.state()}
                </span>
                @if(isPaid)
                    <span class="cb-badge cb-badge-amber shadow-sm">PAID</span>
                @endif
            </div>
            <div class="flex gap-2 text-sm text-gray-500">
                <span class="font-medium text-gray-700">${report.platform()}</span> &bull; 
                <span>Export Pack</span>
            </div>
        </div>
    </div>

    <!-- Navigation Tabs -->
    <div class="border-b border-gray-200 mb-8 overflow-x-auto shrink-0">
        <nav class="-mb-px flex space-x-8" aria-label="Tabs">
            <a href="/c/${report.caseToken()}" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Dashboard</a>
            <a href="/c/${report.caseToken()}/upload" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Upload Evidence</a>
            <a href="/c/${report.caseToken()}/validate" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Validate & Fix</a>
            <a href="/c/${report.caseToken()}/explanation" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Explanation Draft</a>
            <a href="/c/${report.caseToken()}/export" class="border-primary-600 text-primary-600 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors" aria-current="page">Export Pack</a>
        </nav>
    </div>

    <!-- Main Content -->
    <div class="max-w-4xl mx-auto">
        @if(isPaid)
            <div class="cb-card bg-green-50 border-green-200 border text-center p-8 mb-8 rounded-xl shadow-sm">
                <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-green-100 text-green-600 mb-4 shadow"><svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg></div>
                <h2 class="text-2xl font-bold text-green-900 mb-2">Export Pack Unlocked</h2>
                <p class="text-green-800 mb-6 text-sm max-w-lg mx-auto leading-relaxed">Your submission assets are ready. Download the ZIP and summary guide, then submit them in your payment dashboard.</p>
                <div class="flex flex-col sm:flex-row gap-4 justify-center">
                    <a href="/c/${report.caseToken()}/download/submission.zip" class="cb-btn cb-btn-primary bg-green-600 hover:bg-green-700 shadow border-green-600 hover:border-green-700 focus:ring-green-500" data-seo-event="guide_export_click">
                        <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path></svg>
                        Download Submission ZIP
                    </a>
                    <a href="/c/${report.caseToken()}/download/summary.pdf" class="cb-btn cb-btn-secondary border-gray-300 shadow">
                        <svg class="w-4 h-4 mr-1 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z"></path></svg>
                        Download Guide PDF (No Watermark)
                    </a>
                    <a href="/c/${report.caseToken()}/download/explanation.txt" class="cb-btn cb-btn-secondary border-gray-300 shadow">
                        <svg class="w-4 h-4 mr-1 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l4.414 4.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                        Download Explanation TXT
                    </a>
                </div>
            </div>
            
            <div class="bg-gray-50 border border-gray-200 rounded-lg p-4 text-xs text-gray-500 mt-4 leading-relaxed">
                <p><strong>Disclaimer:</strong> This tool improves evidence formatting and consolidates assets. Passing validation means current automated checks passed for ${report.platform()}, but it <strong>does not</strong> guarantee favorable dispute outcomes or provide legal advice.</p>
            </div>
        @else
            <div class="grid grid-cols-1 md:grid-cols-2 gap-8 items-start">
                
                <div class="cb-card border border-gray-200 border-t-4 border-t-primary-500 shadow-lg relative overflow-hidden">
                    <div class="absolute top-0 right-0 p-4">
                        <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold cb-badge-blue bg-blue-100 text-blue-800">RECOMMENDED</span>
                    </div>
                    
                    <h2 class="text-xl font-bold text-gray-900 mb-1">Unlock submission pack</h2>
                    <p class="text-sm text-gray-500 mb-6 pb-6 border-b border-gray-100">Unlock downloadable submission assets and a one-page filing guide.</p>
                    
                    <ul class="space-y-4 mb-8">
                        <li class="flex items-start">
                            <div class="flex-shrink-0 h-5 w-5 text-green-500 mr-3"><svg fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" /></svg></div>
                            <span class="text-sm text-gray-700">Structured <code class="bg-gray-100 px-1 rounded text-xs">submission.zip</code> package with normalized filenames</span>
                        </li>
                        <li class="flex items-start">
                            <div class="flex-shrink-0 h-5 w-5 text-green-500 mr-3"><svg fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" /></svg></div>
                            <span class="text-sm text-gray-700">Auto-fix support for per-type merge and Shopify oversized image compression</span>
                        </li>
                        <li class="flex items-start">
                            <div class="flex-shrink-0 h-5 w-5 text-green-500 mr-3"><svg fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" /></svg></div>
                            <span class="text-sm text-gray-700">Downloadable one-page summary PDF for final submission checklist</span>
                        </li>
                    </ul>
                    
                    <div class="mt-auto">
                        @if(!exportReady)
                            <div class="p-3 bg-amber-50 border-l-4 border-amber-400 text-amber-800 text-xs rounded shadow-sm">
                                <strong>Validation required:</strong> run validation and clear blocked issues before checkout. Current state: <code class="bg-amber-100 font-bold px-1 rounded">${report.state()}</code>.
                            </div>
                        @elseif(!missingRequiredEvidenceTypes.isEmpty())
                            <div class="p-3 bg-amber-50 border-l-4 border-amber-400 text-amber-800 text-xs rounded shadow-sm">
                                <strong>Required evidence missing:</strong> ${String.join(", ", missingRequiredEvidenceTypes)}. Upload these before checkout.
                            </div>
                        @elseif(paymentConfigured)
                            <form method="post" action="/c/${report.caseToken()}/pay">
                                <button type="submit" class="flex justify-center items-center w-full px-4 py-3 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-primary-600 hover:bg-primary-700 transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500">
                                    Pay via ${paymentProviderLabel} to Unlock
                                </button>
                            </form>
                        @else
                            <div class="p-3 bg-red-50 border-l-4 border-red-400 text-red-700 text-xs rounded shadow-sm">
                                <strong>Setup required:</strong> Checkout is not configured. Set billing environment variables (provider and credentials) to enable payment.
                            </div>
                        @endif
                        <p class="text-center text-xs text-gray-400 mt-3 flex items-center justify-center gap-1">
                            <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path></svg>
                            Secure payment processing
                        </p>
                    </div>
                </div>
                
                <!-- Free version panel -->
                <div class="cb-card border border-gray-200">
                    <h2 class="text-lg font-bold text-gray-900 mb-1">Free Tier</h2>
                    <p class="text-sm text-gray-500 mb-6 pb-6 border-b border-gray-100">Validation is free, and you can download a watermarked summary guide.</p>
                    
                    <ul class="space-y-4 mb-8 opacity-75">
                        <li class="flex items-start">
                            <div class="flex-shrink-0 h-5 w-5 text-primary-500 mr-3"><svg fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" /></svg></div>
                            <span class="text-sm text-gray-700 font-medium">Validation rule output</span>
                        </li>
                        <li class="flex items-start">
                            <div class="flex-shrink-0 h-5 w-5 text-primary-500 mr-3"><svg fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" /></svg></div>
                            <span class="text-sm text-gray-700">Watermarked summary PDF (large watermark)</span>
                        </li>
                        <li class="flex items-start">
                            <div class="flex-shrink-0 h-5 w-5 text-gray-300 mr-3"><svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg></div>
                            <span class="text-sm text-gray-500">No ZIP downloads and watermark removal</span>
                        </li>
                    </ul>
                    
                    <div class="space-y-3">
                        @if(exportReady)
                            <a href="/c/${report.caseToken()}/download/summary.pdf" class="flex justify-center items-center w-full px-4 py-2 bg-white text-gray-700 border border-gray-300 rounded text-sm font-medium hover:bg-gray-50 hover:text-gray-900 transition-colors shadow-sm">
                                Download Watermarked Guide PDF
                            </a>
                        @else
                            <button type="button" disabled class="flex justify-center items-center w-full px-4 py-2 bg-gray-100 text-gray-400 border border-gray-200 rounded text-sm font-medium cursor-not-allowed shadow-sm">
                                Run Validation to Unlock Free PDF
                            </button>
                        @endif
                        <a href="/c/${report.caseToken()}/validate" class="flex justify-center items-center w-full px-4 py-2 bg-white text-gray-700 border border-gray-300 rounded text-sm font-medium hover:bg-gray-50 hover:text-gray-900 transition-colors shadow-sm">
                            View Validation Check Output
                        </a>
                    </div>
                </div>
                
            </div>
            
            <div class="bg-gray-50 border border-gray-200 rounded-lg p-4 text-xs text-gray-500 mt-6 leading-relaxed">
                <p><strong>Disclaimer:</strong> This tool helps safely navigate evidence formatting and consolidates assets. It <strong>does not</strong> guarantee favorable legal outcomes or provide legal advice.</p>
            </div>
        @endif
    </div>
`)
