@import com.example.demo.dispute.persistence.DisputeCase
@import com.example.demo.dispute.api.CaseReportResponse
@import com.example.demo.dispute.domain.EvidenceType
@import com.example.demo.dispute.persistence.PaymentEntity
@import com.example.demo.dispute.service.ReasonCodeChecklistService
@import com.example.demo.dispute.service.TailTrimSuggestion
@import com.example.demo.dispute.web.WebCaseController.ManualImageRescueAction
@import com.example.demo.dispute.web.WebCaseController.ManualPdfRescueAction
@import com.example.demo.dispute.web.WebCaseController.NextStepGuidance
@param DisputeCase disputeCase
@param CaseReportResponse report
@param EvidenceType[] evidenceTypes
@param boolean isPaid
@param PaymentEntity latestPayment
@param String message
@param String error
@param ReasonCodeChecklistService.ReasonChecklist reasonChecklist
@param String displayCaseState
@param String displayCaseStateBadgeClass
@param NextStepGuidance nextStepGuidance
@param TailTrimSuggestion tailTrimSuggestion
@param ManualImageRescueAction manualImageRescueAction
@param ManualPdfRescueAction manualPdfRescueAction
@param boolean exportReady
@param boolean minimumCoreEvidenceCoverageReached
@param int minimumCoreEvidenceReadyCount
@param int minimumCoreEvidenceTargetCount
@param String checkoutPriceDisplay

@template.layout(
    title = "Validate | " + report.caseToken(),
    robotsMeta = "noindex,nofollow,noarchive",
    caseTokenForVault = report.caseToken(),
    casePlatformForVault = report.platform().name(),
    caseStateForVault = displayCaseState,
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
    <div class="mb-8 flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div class="min-w-0">
            <div class="mb-2 flex flex-wrap items-center gap-3">
                <h1 class="min-w-0 text-3xl font-bold text-neutral-900 tracking-tight">
                    Case <span class="cb-break-anywhere">${report.caseToken()}</span>
                </h1>
                <span class="cb-badge ${displayCaseStateBadgeClass}">
                    ${displayCaseState}
                </span>
            </div>
            <div class="flex flex-wrap gap-2 text-sm text-gray-500">
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

    @if(report.productScope().name().equals("STRIPE_DISPUTE"))
        <div class="mb-8 p-4 rounded-xl border border-blue-200 bg-blue-50 text-blue-950">
            <div class="font-semibold mb-1">Stripe evidence size note</div>
            <p class="text-sm leading-6">
                Some Stripe File Upload API references mention 5MB uploads, but dispute evidence guidance is stricter at a 4.5MB combined pack limit.
                This validation flow uses the safer 4.5MB rule so a pack marked ready here is less likely to fail in the dispute dashboard.
            </p>
        </div>
    @endif

    @if(report.productScope().name().equals("SHOPIFY_PAYMENTS_CHARGEBACK"))
        <div class="mb-8 p-4 rounded-xl border border-amber-200 bg-amber-50 text-amber-950">
            <div class="font-semibold mb-1">Shopify Payments PDF/A note</div>
            <p class="text-sm leading-6">
                PDF/A status is checked automatically and may differ from Shopify's uploader in edge cases.
                If Shopify still says "PDF/A required" after this tool passes, re-export the original document as PDF/A from your scanner or editor and upload that file again.
            </p>
            <p class="mt-2 text-sm leading-6">
                One Shopify PDF can also fail on portfolio structure or hidden external links at the same time.
                Auto-fix may clear multiple PDF blockers in one pass before you revalidate.
            </p>
        </div>
    @endif

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
                    @if(report.latestValidation().passed() && reasonChecklist.missingRequiredEvidence().isEmpty())
                        <span class="inline-flex items-center px-4 py-1.5 rounded-full text-sm font-semibold bg-green-100 text-green-800 shadow-sm border border-green-200">
                            <svg class="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                            All Checks Passed
                        </span>
                    @elseif(report.latestValidation().passed())
                        <span class="inline-flex items-center px-4 py-1.5 rounded-full text-sm font-semibold bg-amber-100 text-amber-900 shadow-sm border border-amber-200">
                            <svg class="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                            Format Checks Passed
                        </span>
                    @else
                        <span class="inline-flex items-center px-4 py-1.5 rounded-full text-sm font-semibold bg-red-100 text-red-800 shadow-sm border border-red-200">
                            <svg class="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
                            Issues Detected
                        </span>
                    @endif
                </div>
            </div>

            @if(!reasonChecklist.missingRequiredEvidence().isEmpty())
                <div class="mb-6 rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-950">
                    <div class="font-semibold mb-1">Required evidence is still missing</div>
                    <p class="text-sm leading-6">
                        Format checks do not unlock export by themselves. Still needed for this reason:
                        <strong>${String.join(", ", reasonChecklist.missingRequiredEvidence())}</strong>.
                    </p>
                </div>
            @endif

            @if(nextStepGuidance != null)
                <div class="mb-6 rounded-xl border border-sky-200 bg-sky-50 p-5 text-sky-950">
                    <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                        <div class="max-w-2xl">
                            <div class="text-xs font-semibold uppercase tracking-wide text-sky-700">Primary next action</div>
                            <h3 class="mt-1 text-lg font-bold text-sky-950">${nextStepGuidance.title()}</h3>
                            @if(nextStepGuidance.progressNote() != null && !nextStepGuidance.progressNote().isBlank())
                                <div class="mt-2 inline-flex items-center rounded-full border border-sky-200 bg-white px-3 py-1 text-xs font-semibold tracking-wide text-sky-800">
                                    Auto-fix changed: ${nextStepGuidance.progressNote()}
                                </div>
                            @endif
                            <p class="mt-2 text-sm leading-6 text-sky-900">${nextStepGuidance.description()}</p>
                            @if(!nextStepGuidance.supportingActions().isEmpty())
                                <div class="mt-4">
                                    <div class="text-xs font-semibold uppercase tracking-wide text-sky-700">
                                        ${nextStepGuidance.supportingTitle() != null && !nextStepGuidance.supportingTitle().isBlank() ? nextStepGuidance.supportingTitle() : "Priority order"}
                                    </div>
                                    <ol class="mt-2 list-decimal space-y-1 pl-5 text-sm text-sky-900">
                                        @for(String action : nextStepGuidance.supportingActions())
                                            <li>${action}</li>
                                        @endfor
                                    </ol>
                                </div>
                            @endif
                        </div>
                        @if(nextStepGuidance.postAction())
                            <form method="post" action="${nextStepGuidance.ctaTarget()}">
                                <button type="submit" class="cb-btn cb-btn-primary shrink-0 shadow-sm">${nextStepGuidance.ctaLabel()}</button>
                            </form>
                        @else
                            <a href="${nextStepGuidance.ctaTarget()}" class="cb-btn cb-btn-primary shrink-0 shadow-sm">${nextStepGuidance.ctaLabel()}</a>
                        @endif
                    </div>
                </div>
            @endif

            @if(manualImageRescueAction != null)
                <div class="mb-6 rounded-xl border border-emerald-200 bg-emerald-50 p-5 text-emerald-950">
                    <div class="text-xs font-semibold uppercase tracking-wide text-emerald-700">Optional rescue action</div>
                    <h3 class="mt-1 text-lg font-bold text-emerald-950">${manualImageRescueAction.title()}</h3>
                    <p class="mt-2 text-sm leading-6 text-emerald-900">
                        <strong>${manualImageRescueAction.fileLabel()}</strong>
                    </p>
                    <p class="mt-2 text-sm leading-6 text-emerald-900">
                        Current size: <strong>${manualImageRescueAction.currentSizeLabel()}</strong>
                        · Suggested stronger-compression target: <strong>${manualImageRescueAction.targetSizeLabel()}</strong>
                        · Estimated savings: <strong>${manualImageRescueAction.estimatedSavingsLabel()}</strong>
                    </p>
                    <p class="mt-2 text-sm leading-6 text-emerald-900">${manualImageRescueAction.impactSummary()}</p>
                    <p class="mt-2 text-sm leading-6 text-emerald-900">${manualImageRescueAction.caution()}</p>
                    <form method="post" action="/c/${report.caseToken()}/compress-image" class="mt-4">
                        <input type="hidden" name="fileId" value="${manualImageRescueAction.fileId().toString()}">
                        <input type="hidden" name="targetBytes" value="${manualImageRescueAction.targetBytes()}">
                        <button type="submit" class="cb-btn cb-btn-primary shadow-sm">Apply Stronger Compression</button>
                    </form>
                </div>
            @endif

            @if(manualPdfRescueAction != null)
                <div class="mb-6 rounded-xl border border-cyan-200 bg-cyan-50 p-5 text-cyan-950">
                    <div class="text-xs font-semibold uppercase tracking-wide text-cyan-700">Optional rescue action</div>
                    <h3 class="mt-1 text-lg font-bold text-cyan-950">${manualPdfRescueAction.title()}</h3>
                    <p class="mt-2 text-sm leading-6 text-cyan-900">
                        <strong>${manualPdfRescueAction.fileLabel()}</strong>
                    </p>
                    <p class="mt-2 text-sm leading-6 text-cyan-900">
                        Current size: <strong>${manualPdfRescueAction.currentSizeLabel()}</strong>
                        / Suggested stronger-compression target: <strong>${manualPdfRescueAction.targetSizeLabel()}</strong>
                        / Estimated savings: <strong>${manualPdfRescueAction.estimatedSavingsLabel()}</strong>
                    </p>
                    <p class="mt-2 text-sm leading-6 text-cyan-900">${manualPdfRescueAction.impactSummary()}</p>
                    <p class="mt-2 text-sm leading-6 text-cyan-900">${manualPdfRescueAction.caution()}</p>
                    <form method="post" action="/c/${report.caseToken()}/compress-pdf" class="mt-4">
                        <input type="hidden" name="fileId" value="${manualPdfRescueAction.fileId().toString()}">
                        <input type="hidden" name="targetBytes" value="${manualPdfRescueAction.targetBytes()}">
                        <button type="submit" class="cb-btn cb-btn-primary shadow-sm">Apply Stronger PDF Compression</button>
                    </form>
                </div>
            @endif

            @if(tailTrimSuggestion != null)
                <div class="mb-6 rounded-xl border border-amber-200 bg-amber-50 p-5 text-amber-950">
                    <div class="text-xs font-semibold uppercase tracking-wide text-amber-700">Optional review</div>
                    <h3 class="mt-1 text-lg font-bold text-amber-950">${tailTrimSuggestion.title()}</h3>
                    <p class="mt-2 text-sm leading-6 text-amber-900">
                        <strong>${tailTrimSuggestion.fileLabel()} · ${tailTrimSuggestion.pageRange()}</strong>
                    </p>
                    <p class="mt-2 text-sm leading-6 text-amber-900">${tailTrimSuggestion.reason()}</p>
                    <p class="mt-2 text-sm leading-6 text-amber-900">${tailTrimSuggestion.signalSummary()}</p>
                    <p class="mt-2 text-sm leading-6 text-amber-900">${tailTrimSuggestion.caution()}</p>
                    <form method="post" action="/c/${report.caseToken()}/trim-tail" class="mt-4">
                        <input type="hidden" name="fileId" value="${tailTrimSuggestion.fileId().toString()}">
                        <input type="hidden" name="trimStartPage" value="${tailTrimSuggestion.trimStartPage()}">
                        <input type="hidden" name="trimEndPage" value="${tailTrimSuggestion.trimEndPage()}">
                        <button type="submit" class="cb-btn cb-btn-primary shadow-sm">Apply Approved Trim</button>
                    </form>
                </div>
            @endif

            @if(!isPaid)
                <div class="mb-6 rounded-xl border border-slate-200 bg-slate-50 p-5 text-slate-950">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-700">What unlocks after this</div>
                    <div class="mt-4 grid gap-4 md:grid-cols-2 text-sm">
                        <div class="rounded-lg border border-slate-200 bg-white p-4">
                            <div class="font-semibold text-slate-900">Free watermarked summary guide</div>
                            @if(exportReady && minimumCoreEvidenceCoverageReached)
                                <p class="mt-2 text-green-800">Available now. You already cleared the free summary threshold.</p>
                            @elseif(exportReady && minimumCoreEvidenceTargetCount > 0)
                                <p class="mt-2 text-amber-900">
                                    Still locked for thin evidence.
                                    Current core coverage: <strong>${minimumCoreEvidenceReadyCount} / ${minimumCoreEvidenceTargetCount}</strong>.
                                </p>
                            @else
                                <p class="mt-2 text-slate-700">Unlocks after the remaining blockers are cleared and the case reaches export-ready state.</p>
                            @endif
                        </div>
                        <div class="rounded-lg border border-slate-200 bg-white p-4">
                            <div class="font-semibold text-slate-900">Paid final pack</div>
                            <p class="mt-2 text-slate-700">
                                After the case is export-ready, unlock the final ZIP, guide PDF without watermark, and explanation draft for
                                <strong>${checkoutPriceDisplay}</strong>.
                            </p>
                        </div>
                    </div>
                </div>
            @endif

            @if(report.latestValidation().issues().isEmpty())
                @if(reasonChecklist.missingRequiredEvidence().isEmpty())
                    <div class="bg-green-50 border border-green-200 rounded-xl p-8 text-center text-green-900 flex flex-col items-center">
                        <svg class="w-12 h-12 text-green-500 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                        <h3 class="text-xl font-bold mb-2">Format checks passed</h3>
                        <p class="max-w-md mx-auto text-green-700 mb-6">Your documents meet current format rules and the required evidence checklist is complete. Review the export pack next.</p>
                        <a href="/c/${report.caseToken()}/export" class="cb-btn bg-green-600 text-white hover:bg-green-700 shadow-sm font-semibold">Review Export</a>
                    </div>
                @else
                    <div class="bg-amber-50 border border-amber-200 rounded-xl p-8 text-center text-amber-950 flex flex-col items-center">
                        <svg class="w-12 h-12 text-amber-600 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                        <h3 class="text-xl font-bold mb-2">Format checks passed. Evidence checklist still incomplete.</h3>
                        <p class="max-w-lg mx-auto text-amber-900 mb-6">Validation only confirms format, size, and rule checks. Upload the missing required evidence before export review or checkout.</p>
                        <div class="flex flex-col sm:flex-row gap-3">
                            <a href="/c/${report.caseToken()}/upload" class="cb-btn cb-btn-primary shadow-sm font-semibold">Upload Missing Evidence</a>
                            <a href="/c/${report.caseToken()}/export" class="cb-btn cb-btn-secondary shadow-sm font-semibold">Review Export Requirements</a>
                        </div>
                    </div>
                @endif
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
                                        @if(issue.guideSlug() != null && !issue.guideSlug().isBlank())
                                            <div class="mt-2">
                                                <a
                                                    href="${"/guides/" + report.platform().name().toLowerCase() + "/" + issue.guideSlug()}"
                                                    class="inline-flex items-center text-xs font-semibold text-blue-700 hover:text-blue-900"
                                                >
                                                    Open exact fix guide
                                                </a>
                                            </div>
                                        @endif
                                    </td>
                                </tr>
                            @endfor
                        </tbody>
                    </table>
                </div>
                
                <div class="mt-8 bg-blue-50 border border-blue-100 rounded-xl p-6 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
                    <div>
                        <h4 class="font-bold text-blue-900 mb-1">Let us fix these issues automatically</h4>
                        <p class="text-sm text-blue-800">Our auto-fix engine merges duplicate per-type files, compresses supported oversized packs, removes blank or duplicate PDF pages, and strips PDF external links when detected.</p>
                    </div>
                    <form method="post" action="/c/${report.caseToken()}/fix">
                        <button type="submit" class="cb-btn cb-btn-primary bg-blue-600 hover:bg-blue-700 hover:-translate-y-0.5 transition-transform shrink-0 shadow">Run Auto-Fix Engine</button>
                    </form>
                </div>
            @endif
        </div>
    @endif
`)
