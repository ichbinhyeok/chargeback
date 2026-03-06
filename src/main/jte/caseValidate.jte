@import com.example.demo.dispute.persistence.DisputeCase
@import com.example.demo.dispute.api.CaseReportResponse
@import com.example.demo.dispute.domain.EvidenceType
@import com.example.demo.dispute.persistence.PaymentEntity
@param DisputeCase disputeCase
@param CaseReportResponse report
@param EvidenceType[] evidenceTypes
@param boolean isPaid
@param PaymentEntity latestPayment
@param String message
@param String error

@template.layout(
    title = "Validate | " + report.caseToken(),
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

    <!-- Header Section -->
    <div class="mb-8 flex flex-col md:flex-row md:items-start justify-between gap-4">
        <div>
            <div class="flex items-center gap-3 mb-2">
                <h1 class="text-3xl font-bold text-neutral-900 tracking-tight">Case ${report.caseToken()}</h1>
                <span class="cb-badge ${(report.state().name().equals("READY") || report.state().name().equals("PAID") || report.state().name().equals("DOWNLOADED")) ? "cb-badge-green" : (report.state().name().equals("BLOCKED") ? "cb-badge-red" : "cb-badge-blue")}">
                    ${report.state()}
                </span>
            </div>
            <div class="flex gap-2 text-sm text-gray-500">
                <span class="font-medium text-gray-700">${report.platform()}</span> &bull; 
                <span>Validation Results</span>
            </div>
        </div>
        <div class="flex flex-col sm:flex-row items-center gap-3 border-t md:border-t-0 pt-4 md:pt-0 border-gray-100">
            <form method="post" action="/c/${report.caseToken()}/validate">
                <button type="submit" class="cb-btn cb-btn-secondary text-sm shadow-sm transition-colors hover:bg-gray-50">
                    Re-run Validation
                </button>
            </form>
            <form method="post" action="/c/${report.caseToken()}/fix">
                <button type="submit" class="cb-btn cb-btn-primary text-sm shadow-sm flex items-center gap-2 hover:bg-blue-700">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path></svg>
                    Run Auto-Fix
                </button>
            </form>
        </div>
    </div>

    <!-- Navigation Tabs -->
    <div class="border-b border-gray-200 mb-8 overflow-x-auto shrink-0">
        <nav class="-mb-px flex space-x-8" aria-label="Tabs">
            <a href="/c/${report.caseToken()}" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Dashboard</a>
            <a href="/c/${report.caseToken()}/upload" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Upload Evidence</a>
            <a href="/c/${report.caseToken()}/validate" class="border-primary-600 text-primary-600 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors" aria-current="page">Validate & Fix</a>
            <a href="/c/${report.caseToken()}/explanation" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Explanation Draft</a>
            <a href="/c/${report.caseToken()}/export" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Export Pack</a>
        </nav>
    </div>

    <!-- Main Content -->
    @if(report.latestValidation() == null)
        <div class="cb-card flex-1 flex flex-col items-center justify-center text-center p-12 border-2 border-dashed border-gray-200 bg-gray-50">
            <svg class="w-16 h-16 text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.957 11.957 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path></svg>
            <h3 class="text-xl font-bold text-gray-900 mb-2">No Validation Scans Yet</h3>
            <p class="text-gray-500 mb-6 max-w-md">Upload your evidence files and then run the validation engine to see if there are any format, size, or content issues.</p>
            <form method="post" action="/c/${report.caseToken()}/validate">
                <button type="submit" class="cb-btn cb-btn-primary px-6 shadow-sm hover:-translate-y-0.5 transition-transform">Run First Validation Scan</button>
            </form>
        </div>
    @else
        <div class="cb-card border border-gray-100 shadow-sm mb-8">
            <div class="flex items-center justify-between mb-6 pb-6 border-b border-gray-100">
                <div>
                    <h2 class="text-lg font-bold text-gray-900">Validation Results</h2>
                    <p class="text-sm text-gray-500 mt-1">Run #${report.latestValidation().runNo()} &bull; Validated for ${report.latestValidation().source()}</p>
                </div>
                <div>
                    @if(report.latestValidation().passed())
                        <span class="inline-flex items-center px-4 py-1.5 rounded-full text-sm font-semibold bg-green-100 text-green-800 shadow-sm border border-green-200">
                            <svg class="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                            All Checks Passed
                        </span>
                    @else
                        <span class="inline-flex items-center px-4 py-1.5 rounded-full text-sm font-semibold bg-red-100 text-red-800 shadow-sm border border-red-200">
                            <svg class="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
                            Issues Detected
                        </span>
                    @endif
                </div>
            </div>

            @if(report.latestValidation().issues().isEmpty())
                <div class="bg-green-50 border border-green-200 rounded-xl p-8 text-center text-green-900 flex flex-col items-center">
                    <svg class="w-12 h-12 text-green-500 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                    <h3 class="text-xl font-bold mb-2">Perfect Format!</h3>
                    <p class="max-w-md mx-auto text-green-700 mb-6">Your documents meet all criteria. You can now securely export the finalized submission zip.</p>
                    <a href="/c/${report.caseToken()}/export" class="cb-btn bg-green-600 text-white hover:bg-green-700 shadow-sm font-semibold">Proceed to Export</a>
                </div>
            @else
                <div class="overflow-hidden rounded-xl border border-gray-200 shadow-sm">
                    <table class="min-w-full divide-y divide-gray-200 relative">
                        <thead class="bg-gray-50 border-b border-gray-200">
                            <tr>
                                <th scope="col" class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Severity</th>
                                <th scope="col" class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider hidden sm:table-cell">Issue Code</th>
                                <th scope="col" class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider hidden lg:table-cell">Rule</th>
                                <th scope="col" class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Resolution Guidance</th>
                            </tr>
                        </thead>
                        <tbody class="bg-white divide-y divide-gray-200">
                            @for(var issue : report.latestValidation().issues())
                                <tr class="hover:bg-gray-50 transition-colors">
                                    <td class="px-6 py-4 whitespace-nowrap">
                                        @if(issue.severity().name().equals("BLOCKED"))
                                            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold cb-badge-red">BLOCKED</span>
                                        @elseif(issue.severity().name().equals("FIXABLE"))
                                            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold cb-badge-amber">FIXABLE</span>
                                        @else
                                            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold cb-badge-blue">WARNING</span>
                                        @endif
                                    </td>
                                    <td class="px-6 py-4 whitespace-nowrap font-mono text-sm text-gray-500 hidden sm:table-cell">
                                        ${issue.code()}
                                    </td>
                                    <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900 hidden lg:table-cell">
                                        ${issue.ruleId()}
                                    </td>
                                    <td class="px-6 py-4 text-sm text-gray-600 leading-relaxed font-medium">
                                        ${issue.message()}
                                    </td>
                                </tr>
                            @endfor
                        </tbody>
                    </table>
                </div>
                
                <div class="mt-8 bg-blue-50 border border-blue-100 rounded-xl p-6 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
                    <div>
                        <h4 class="font-bold text-blue-900 mb-1">Let us fix these issues automatically</h4>
                        <p class="text-sm text-blue-800">Our auto-fix engine repairs image compression and merges multi-file types like <code class="bg-blue-100 px-1 rounded mx-0.5 text-xs text-blue-900 font-bold">receipt</code> where possible.</p>
                    </div>
                    <form method="post" action="/c/${report.caseToken()}/fix">
                        <button type="submit" class="cb-btn cb-btn-primary bg-blue-600 hover:bg-blue-700 hover:-translate-y-0.5 transition-transform shrink-0 shadow">Run Auto-Fix Engine</button>
                    </form>
                </div>
            @endif
        </div>
    @endif
`)
