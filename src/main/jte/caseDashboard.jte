@import com.example.demo.dispute.persistence.DisputeCase
@import com.example.demo.dispute.api.CaseReportResponse
@import com.example.demo.dispute.domain.EvidenceType
@import com.example.demo.dispute.persistence.PaymentEntity
@import com.example.demo.dispute.service.ReasonCodeChecklistService
@import java.util.List
@param DisputeCase disputeCase
@param CaseReportResponse report
@param EvidenceType[] evidenceTypes
@param boolean isPaid
@param PaymentEntity latestPayment
@param String message
@param String error
@param Integer readinessScore
@param String readinessLabel
@param Integer readinessBlocked
@param Integer readinessFixable
@param Integer readinessWarning
@param List<String> missingEvidenceTypes
@param List<String> missingRequiredEvidenceTypes
@param List<String> missingRecommendedEvidenceTypes
@param ReasonCodeChecklistService.ReasonChecklist reasonChecklist
@param Integer retentionDays
@param Integer retentionDueDateBufferDays
@param String expiresAtText
@param String casePublicUrl
@param String displayCaseState
@param String displayCaseStateBadgeClass

@template.layout(
    title = "Case " + report.caseToken(),
    robotsMeta = "noindex,nofollow,noarchive",
    caseTokenForVault = report.caseToken(),
    casePlatformForVault = report.platform().name(),
    caseStateForVault = displayCaseState,
    content = @`
    <!-- Alerts -->
    @if(message != null && !message.isBlank())
        <div class="mb-6 p-4 rounded-lg bg-green-50 text-green-800 border border-green-200 flex items-center gap-3 shadow-sm">
            <svg class="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
            <span class="font-medium">${message}</span>
        </div>
    @endif
    @if(error != null && !error.isBlank())
        <div class="mb-6 p-4 rounded-lg bg-red-50 text-red-800 border border-red-200 flex items-center gap-3 shadow-sm">
            <svg class="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
            <span class="font-medium">${error}</span>
        </div>
    @endif

    <!-- Header Section -->
    <div class="mb-8 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div class="min-w-0">
            <div class="mb-2 flex flex-wrap items-center gap-3">
                <h1 class="min-w-0 text-3xl font-bold text-neutral-900 tracking-tight">
                    Case <span class="cb-break-anywhere">${report.caseToken()}</span>
                </h1>
                <span class="cb-badge ${displayCaseStateBadgeClass}">
                    ${displayCaseState}
                </span>
                @if(isPaid)
                    <span class="cb-badge cb-badge-amber">PAID</span>
                @endif
            </div>
            <div class="flex flex-wrap gap-2 text-sm text-gray-500">
                <span class="font-medium text-gray-700">${report.platform()}</span> &bull; 
                <span>${report.productScope()}</span>
            </div>
        </div>
        <div class="flex flex-wrap items-center gap-3 self-start">
            <form method="post" action="/c/${report.caseToken()}/delete" onsubmit="return confirm('Delete this case and all files now?');">
                <button type="submit" class="cb-btn cb-btn-danger whitespace-nowrap text-sm shadow-sm">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                    Delete Case
                </button>
            </form>
        </div>
    </div>

    <!-- Navigation Tabs -->
    <div class="border-b border-gray-200 mb-8 overflow-x-auto shrink-0">
        <nav class="-mb-px flex space-x-8" aria-label="Tabs">
            <a href="/c/${report.caseToken()}" class="border-primary-600 text-primary-600 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors" aria-current="page">Dashboard</a>
            <a href="/c/${report.caseToken()}/upload" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Upload Evidence</a>
            <a href="/c/${report.caseToken()}/validate" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Validate & Fix</a>
            <a href="/c/${report.caseToken()}/explanation" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Explanation Draft</a>
            <a href="/c/${report.caseToken()}/export" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Export Pack</a>
        </nav>
    </div>

    @if(report.files().isEmpty())
        <section class="mb-8 rounded-xl border border-blue-200 bg-blue-50 p-5 text-blue-950">
            <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <h2 class="text-sm font-semibold uppercase tracking-wide">What to click next</h2>
                    <p class="mt-2 text-sm leading-6">Upload your first evidence files from the next tab. Nothing is submitted yet on this dashboard.</p>
                    <ol class="mt-3 space-y-1 pl-5 text-sm list-decimal">
                        <li>Open <strong>Upload Evidence</strong>.</li>
                        <li>Choose files and confirm the evidence type in the mapping modal.</li>
                        <li>After upload, review validation results before export.</li>
                    </ol>
                </div>
                <a href="/c/${report.caseToken()}/upload" class="cb-btn cb-btn-primary shrink-0">Upload Your First Files</a>
            </div>
        </section>
    @endif

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
        <section class="cb-card">
            <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Submission Readiness</h2>
            <div class="flex items-end justify-between mb-3">
                <p class="text-4xl font-bold text-gray-900">${readinessScore}%</p>
                <span class="cb-badge ${(readinessScore >= 90) ? "cb-badge-green" : (readinessScore >= 70 ? "cb-badge-blue" : (readinessScore >= 50 ? "cb-badge-amber" : "cb-badge-red"))}">
                    ${readinessLabel}
                </span>
            </div>
            <div class="space-y-1 text-sm text-gray-600">
                <div>Blocked: <strong class="text-gray-900">${readinessBlocked}</strong></div>
                <div>Fixable: <strong class="text-gray-900">${readinessFixable}</strong></div>
                <div>Warnings: <strong class="text-gray-900">${readinessWarning}</strong></div>
                <div>Missing Types: <strong class="text-gray-900">${missingEvidenceTypes.size()}</strong></div>
                <div>Missing Required: <strong class="text-gray-900">${missingRequiredEvidenceTypes.size()}</strong></div>
                <div>Missing Recommended: <strong class="text-gray-900">${missingRecommendedEvidenceTypes.size()}</strong></div>
            </div>
            @if(!missingRequiredEvidenceTypes.isEmpty())
                <p class="mt-3 text-xs text-red-700">Required: ${String.join(", ", missingRequiredEvidenceTypes.subList(0, Math.min(4, missingRequiredEvidenceTypes.size())))}@if(missingRequiredEvidenceTypes.size() > 4), ...@endif</p>
            @endif
            @if(!missingRecommendedEvidenceTypes.isEmpty())
                <p class="mt-2 text-xs text-gray-500">Recommended: ${String.join(", ", missingRecommendedEvidenceTypes.subList(0, Math.min(4, missingRecommendedEvidenceTypes.size())))}@if(missingRecommendedEvidenceTypes.size() > 4), ...@endif</p>
            @endif
        </section>

        <section class="cb-card">
            <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Access & Recovery</h2>
            <div class="space-y-2">
                <button type="button" class="cb-btn cb-btn-secondary w-full" onclick="navigator.clipboard.writeText('${casePublicUrl}')">Copy Case Link</button>
                <button type="button" class="cb-btn cb-btn-secondary w-full" onclick="navigator.clipboard.writeText('${report.caseToken()}')">Copy Case Token</button>
                <a href="/c/${report.caseToken()}/access-key.txt" class="cb-btn cb-btn-secondary w-full">Download Access Key</a>
                <form method="post" action="/c/${report.caseToken()}/rotate-token" onsubmit="return confirm('Rotate token now? Old link will stop working immediately.');">
                    <button type="submit" class="cb-btn cb-btn-danger w-full">Rotate Access Token</button>
                </form>
            </div>
            <p class="mt-3 text-xs text-gray-500">Anyone with this token can access your case. Rotate if you suspect link sharing.</p>
        </section>

        <section class="cb-card">
            <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Retention</h2>
            <p class="text-sm text-gray-700">Automatic deletion after <strong>${retentionDays} days</strong> by default.</p>
            <p class="text-sm text-gray-700 mt-1">If due date is set, expiry extends to <strong>due date + ${retentionDueDateBufferDays} day(s)</strong> when later.</p>
            <p class="text-sm text-gray-700 mt-1">Scheduled deletion: <strong>${expiresAtText}</strong></p>
            <p class="mt-3 text-xs text-gray-500">Use Delete Case now for immediate removal of files and metadata.</p>
        </section>
    </div>

    <section class="cb-card mb-8">
        <div class="flex flex-col md:flex-row md:items-start md:justify-between gap-4 mb-4">
            <div>
                <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide">Reason-Code Evidence Checklist</h2>
                <p class="text-xs text-gray-500 mt-1">Policy-aware evidence requirements for the selected dispute reason.</p>
            </div>
            @if(reasonChecklist.reasonInput() != null && !reasonChecklist.reasonInput().isBlank())
                <div class="text-xs text-gray-500">
                    Input: <code class="bg-gray-100 px-1.5 py-0.5 rounded">${reasonChecklist.reasonInput()}</code>
                </div>
            @endif
        </div>

        @if(!reasonChecklist.hasReasonCode())
            <div class="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg p-3">
                Reason code is not set. Add a reason code to get reason-specific evidence guidance.
            </div>
        @else
            <div class="mb-4 flex flex-wrap items-center gap-2">
                <span class="cb-badge cb-badge-blue">${reasonChecklist.reasonLabel()}</span>
                <span class="text-xs text-gray-500">Canonical key: <code class="bg-gray-100 px-1 rounded">${reasonChecklist.canonicalReasonKey()}</code></span>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                    <h3 class="text-xs font-semibold text-gray-700 uppercase tracking-wide mb-2">Required Evidence</h3>
                    @if(reasonChecklist.requiredEvidence().isEmpty())
                        <p class="text-sm text-gray-500">No required evidence mapped for this reason.</p>
                    @else
                        <ul class="space-y-1 text-sm text-gray-700">
                            @for(var item : reasonChecklist.requiredEvidence())
                                <li>
                                    @if(reasonChecklist.missingRequiredEvidence().contains(item))
                                        <span class="text-red-700 font-medium">Missing:</span> ${item}
                                    @else
                                        <span class="text-green-700 font-medium">Ready:</span> ${item}
                                    @endif
                                </li>
                            @endfor
                        </ul>
                    @endif
                </div>
                <div>
                    <h3 class="text-xs font-semibold text-gray-700 uppercase tracking-wide mb-2">Recommended Evidence</h3>
                    @if(reasonChecklist.recommendedEvidence().isEmpty())
                        <p class="text-sm text-gray-500">No recommended evidence mapped for this reason.</p>
                    @else
                        <ul class="space-y-1 text-sm text-gray-700">
                            @for(var item : reasonChecklist.recommendedEvidence())
                                <li>
                                    @if(reasonChecklist.missingRecommendedEvidence().contains(item))
                                        <span class="text-amber-700 font-medium">Missing:</span> ${item}
                                    @else
                                        <span class="text-green-700 font-medium">Ready:</span> ${item}
                                    @endif
                                </li>
                            @endfor
                        </ul>
                    @endif
                </div>
            </div>

            @if(!reasonChecklist.weakEvidenceWarnings().isEmpty())
                <div class="mt-5">
                    <h3 class="text-xs font-semibold text-gray-700 uppercase tracking-wide mb-2">Weak-Evidence Warnings</h3>
                    <ul class="space-y-1 text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded-lg p-3">
                        @for(var warning : reasonChecklist.weakEvidenceWarnings())
                            <li>${warning}</li>
                        @endfor
                    </ul>
                </div>
            @endif

            @if(!reasonChecklist.priorityActions().isEmpty())
                <div class="mt-5">
                    <h3 class="text-xs font-semibold text-gray-700 uppercase tracking-wide mb-2">Priority Actions</h3>
                    <ul class="space-y-1 text-sm text-gray-700">
                        @for(var action : reasonChecklist.priorityActions())
                            <li>${action}</li>
                        @endfor
                    </ul>
                </div>
            @endif

            @if(!reasonChecklist.sourceUrls().isEmpty())
                <div class="mt-5">
                    <h3 class="text-xs font-semibold text-gray-700 uppercase tracking-wide mb-2">Policy Sources</h3>
                    <div class="flex flex-wrap gap-2 text-xs">
                        @for(var url : reasonChecklist.sourceUrls())
                            <a href="${url}" target="_blank" rel="noreferrer noopener" class="inline-flex items-center px-2 py-1 rounded border border-gray-300 text-gray-700 hover:bg-gray-50">${url}</a>
                        @endfor
                    </div>
                </div>
            @endif
        @endif
    </section>

    <!-- Main Content -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
        
        <!-- Uploaded Files summary -->
        <div class="cb-card h-full flex flex-col group relative">
            <div class="flex justify-between items-center mb-6">
                <h2 class="text-lg font-bold text-gray-900 group-hover:text-primary-600 transition-colors">Uploaded Evidence</h2>
                <a href="/c/${report.caseToken()}/upload" class="text-sm font-medium text-primary-600 hover:text-primary-700">Manage docs &rarr;</a>
            </div>
            
            @if(report.files().isEmpty())
                <div class="flex-1 flex flex-col items-center justify-center text-center p-6 border-2 border-dashed border-gray-200 rounded-xl bg-gray-50 text-gray-500">
                    <svg class="w-12 h-12 text-gray-400 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path></svg>
                    <p class="font-medium text-gray-900 mb-1">No files yet</p>
                    <p class="text-sm mb-4">Start by uploading your evidence documents.</p>
                    <a href="/c/${report.caseToken()}/upload" class="cb-btn cb-btn-secondary text-sm">Upload Evidence</a>
                </div>
            @else
                <div class="overflow-x-auto flex-1">
                    <table class="w-full text-sm text-left">
                        <thead class="text-xs text-gray-500 uppercase bg-gray-50">
                            <tr>
                                <th class="px-4 py-3 rounded-l-lg">Type</th>
                                <th class="px-4 py-3 text-center">Format</th>
                                <th class="px-4 py-3 rounded-r-lg text-right">Size</th>
                            </tr>
                        </thead>
                        <tbody>
                            @for(var file : report.files())
                                <tr class="border-b border-gray-50 last:border-0 hover:bg-gray-50 transition-colors">
                                    <td class="px-4 py-3 font-medium text-gray-900">${file.evidenceType()}</td>
                                    <td class="px-4 py-3 text-center">
                                        <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-gray-100 text-gray-600 uppercase tracking-wider">
                                            ${file.fileFormat()}
                                        </span>
                                    </td>
                                    <td class="px-4 py-3 text-right text-gray-500 font-mono text-xs">${String.format("%.1f", file.sizeBytes() / 1024.0)} KB</td>
                                </tr>
                            @endfor
                        </tbody>
                    </table>
                </div>
            @endif
        </div>

        <!-- Validation summary -->
        <div class="cb-card h-full flex flex-col group">
            <div class="flex justify-between items-center mb-6">
                <h2 class="text-lg font-bold text-gray-900 group-hover:text-primary-600 transition-colors">Latest Validation</h2>
                <a href="/c/${report.caseToken()}/validate" class="text-sm font-medium text-primary-600 hover:text-primary-700">View details &rarr;</a>
            </div>

            @if(report.latestValidation() == null)
                <div class="flex-1 flex flex-col items-center justify-center text-center p-6 border-2 border-dashed border-gray-200 rounded-xl bg-gray-50 text-gray-500">
                    <svg class="w-12 h-12 text-gray-400 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.957 11.957 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path></svg>
                    <p class="font-medium text-gray-900 mb-1">No scan completed</p>
                    <p class="text-sm">Upload files and run the validation engine.</p>
                </div>
            @else
                <div class="mb-4 bg-gray-50 p-3 rounded-lg flex items-center justify-between border border-gray-100">
                    <div class="flex items-center gap-2">
                        @if(report.latestValidation().passed())
                            <span class="cb-badge cb-badge-green text-sm px-3 py-1">Passed</span>
                        @else
                            <span class="cb-badge cb-badge-red text-sm px-3 py-1">Failed</span>
                        @endif
                        <span class="text-xs text-gray-500 uppercase tracking-wide font-medium">Run #${report.latestValidation().runNo()}</span>
                    </div>
                </div>
                
                    <div class="overflow-y-auto max-h-[300px] flex-1 pr-2 space-y-3 custom-scrollbar">
                    @if(report.latestValidation().issues().isEmpty())
                        @if(reasonChecklist.missingRequiredEvidence().isEmpty())
                            <div class="p-4 bg-green-50 rounded-lg border border-green-100 text-green-800 text-sm flex gap-3">
                                <svg class="w-5 h-5 text-green-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                                <div>
                                    <p class="font-semibold text-green-900">Format checks passed and required evidence is present.</p>
                                    <p class="mt-1">Open <strong>Export Pack</strong> to review the final pack and checkout state.</p>
                                </div>
                            </div>
                        @else
                            <div class="p-4 bg-amber-50 rounded-lg border border-amber-200 text-amber-900 text-sm flex gap-3">
                                <svg class="w-5 h-5 text-amber-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                                <div>
                                    <p class="font-semibold text-amber-950">Format checks passed, but required evidence is still missing.</p>
                                    <p class="mt-1">Still needed: <strong>${String.join(", ", reasonChecklist.missingRequiredEvidence())}</strong>.</p>
                                </div>
                            </div>
                        @endif
                    @else
                        @for(var issue : report.latestValidation().issues())
                            <div class="p-3 bg-red-50 border-l-4 border-red-500 rounded-lg text-sm text-red-900 relative">
                                <span class="absolute top-3 right-3 font-mono text-xs font-bold text-red-500 bg-red-100 px-1.5 py-0.5 rounded">${issue.code()}</span>
                                <div class="font-bold border-b border-red-200 pb-1 mb-1 max-w-[80%]">${issue.severity()}</div>
                                <p class="mt-1.5 opacity-90 leading-relaxed">${issue.message()}</p>
                            </div>
                        @endfor
                    @endif
                </div>
            @endif
        </div>
    </div>
`)
