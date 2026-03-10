@import com.example.demo.dispute.persistence.DisputeCase
@import com.example.demo.dispute.api.CaseReportResponse
@import com.example.demo.dispute.domain.EvidenceType
@import com.example.demo.dispute.persistence.PaymentEntity
@import com.example.demo.dispute.web.CaseUiLabels
@param DisputeCase disputeCase
@param CaseReportResponse report
@param EvidenceType[] evidenceTypes
@param boolean isPaid
@param PaymentEntity latestPayment
@param String displayCaseState
@param String displayCaseStateBadgeClass

@template.layout(
    title = "Report | " + report.caseToken(),
    robotsMeta = "noindex,nofollow,noarchive",
    caseTokenForVault = report.caseToken(),
    casePlatformForVault = report.platform().name(),
    caseStateForVault = displayCaseState,
    content = @`
    <div class="max-w-4xl mx-auto">
        <div class="mb-6">
            <a href="/c/${report.caseToken()}" class="inline-flex items-center text-sm font-medium text-gray-500 hover:text-gray-900 transition-colors">
                <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path></svg>
                Back to Overview
            </a>
        </div>

        <div class="cb-card mb-8">
            <div class="border-b border-gray-100 pb-5 mb-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <h1 class="text-2xl font-bold text-neutral-800 tracking-tight">Case Report</h1>
                    <p class="text-gray-500 text-sm mt-1 uppercase tracking-wider font-mono">${report.caseToken()}</p>
                </div>
                <div class="flex items-center gap-2">
                    <span class="cb-badge cb-badge-gray">${CaseUiLabels.platformLabel(report.platform())}</span>
                    <span class="cb-badge ${displayCaseStateBadgeClass}">${displayCaseState}</span>
                    @if(isPaid)
                        <span class="cb-badge cb-badge-amber">PAID</span>
                    @endif
                </div>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
                <!-- Validation Report -->
                <div>
                    <h2 class="text-sm font-bold text-gray-900 uppercase tracking-widest mb-4 border-b border-gray-100 pb-2">Validation Summary</h2>
                    @if(report.latestValidation() == null)
                        <div class="p-4 bg-gray-50 rounded-lg text-sm text-gray-500 italic border border-gray-200">
                            No validation run yet.
                        </div>
                    @else
                        @if(report.latestValidation().issues().isEmpty())
                            <div class="p-4 bg-green-50 rounded-lg border border-green-200 text-green-800 text-sm flex gap-3">
                                <svg class="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                                No format issues detected for ${CaseUiLabels.validationSourceLabel(report.latestValidation().source())}.
                            </div>
                        @else
                            <ul class="space-y-3">
                                @for(var issue : report.latestValidation().issues())
                                    <li class="p-3 bg-red-50 border-l-2 border-red-500 rounded text-sm text-red-900">
                                        <div class="flex justify-between items-center mb-1">
                                            <span class="font-bold border-b border-red-300 pb-0.5">${issue.severity()}</span>
                                            <span class="font-mono text-[10px] bg-red-100 text-red-800 px-1 py-0.5 rounded">${issue.code()}</span>
                                        </div>
                                        <p class="mt-1 opacity-90 leading-snug">${issue.message()}</p>
                                    </li>
                                @endfor
                            </ul>
                        @endif
                    @endif
                </div>

                <!-- Files Report -->
                <div>
                    <h2 class="text-sm font-bold text-gray-900 uppercase tracking-widest mb-4 border-b border-gray-100 pb-2">Files Summary</h2>
                    @if(report.files().isEmpty())
                        <div class="p-4 bg-gray-50 rounded-lg text-sm text-gray-500 italic border border-gray-200">
                            No files uploaded.
                        </div>
                    @else
                        <ul class="space-y-2">
                            @for(var file : report.files())
                                <li class="p-3 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-800 flex justify-between items-center">
                                    <div class="font-medium">${CaseUiLabels.evidenceTypeLabel(file.evidenceType())}</div>
                                    <div class="flex gap-3 text-xs text-gray-500">
                                        <span class="uppercase font-semibold tracking-wider">${file.fileFormat()}</span>
                                        <span class="font-mono">${String.format("%.1f", file.sizeBytes() / 1024.0)} KB</span>
                                        <span class="bg-gray-200 text-gray-600 px-1.5 py-0.5 rounded">${file.pageCount()}p</span>
                                    </div>
                                </li>
                            @endfor
                        </ul>
                    @endif
                </div>
            </div>
        </div>
    </div>
`)
