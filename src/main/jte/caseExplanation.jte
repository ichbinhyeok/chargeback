@import com.example.demo.dispute.persistence.DisputeCase
@import com.example.demo.dispute.api.CaseReportResponse
@import com.example.demo.dispute.domain.EvidenceType
@import com.example.demo.dispute.persistence.PaymentEntity
@import com.example.demo.dispute.service.DisputeExplanationService
@import com.example.demo.dispute.web.CaseUiLabels
@param DisputeCase disputeCase
@param CaseReportResponse report
@param EvidenceType[] evidenceTypes
@param boolean isPaid
@param PaymentEntity latestPayment
@param String message
@param String error
@param DisputeExplanationService.ExplanationDraft explanationDraft
@param String displayCaseState
@param String displayCaseStateBadgeClass

@template.layout(
    title = "Explanation | " + report.caseToken(),
    robotsMeta = "noindex,nofollow,noarchive",
    caseTokenForVault = report.caseToken(),
    casePlatformForVault = report.platform().name(),
    caseStateForVault = displayCaseState,
    caseStage = "review",
    content = @`
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
                <span class="font-medium text-gray-700">${CaseUiLabels.platformLabel(report.platform())}</span> &bull;
                <span>${CaseUiLabels.productScopeLabel(report.productScope())}</span> &bull;
                <span>Dispute Explanation Draft</span>
            </div>
        </div>
    </div>

    <div class="border-b border-gray-200 mb-8 overflow-x-auto shrink-0">
        <nav class="-mb-px flex space-x-8" aria-label="Tabs">
            <a href="/c/${report.caseToken()}" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Overview</a>
            <a href="/c/${report.caseToken()}/upload" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Upload</a>
            <a href="/c/${report.caseToken()}/validate" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Review &amp; Fix</a>
            <a href="/c/${report.caseToken()}/explanation" class="border-primary-600 text-primary-600 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors" aria-current="page">Draft Response</a>
            <a href="/c/${report.caseToken()}/export" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Export</a>
        </nav>
    </div>

    <div class="cb-card mb-6">
        <div class="flex flex-col md:flex-row md:items-start md:justify-between gap-3 mb-4">
            <div>
                <h2 class="text-lg font-bold text-gray-900">${explanationDraft.title()}</h2>
                <p class="text-sm text-gray-600 mt-1">${explanationDraft.contextLine()}</p>
            </div>
            <div class="flex items-center gap-2">
                <button type="button" class="cb-btn cb-btn-secondary text-sm" onclick="copyExplanation()">Copy Text</button>
                <a href="/c/${report.caseToken()}/download/explanation.txt" class="cb-btn cb-btn-secondary text-sm">Download TXT</a>
                <button type="button" class="cb-btn cb-btn-primary text-sm" onclick="downloadEditedText()">Download Edited TXT</button>
            </div>
        </div>
        <p class="text-sm text-gray-700 mb-4">${explanationDraft.summaryLine()}</p>
        <textarea id="explanation-text" class="cb-input w-full min-h-[420px] font-mono text-xs leading-6">${explanationDraft.text()}</textarea>
        <p class="text-xs text-gray-500 mt-3">
            This draft is generated from current evidence metadata and checklist policy. Edit wording with transaction-specific facts before submission.
        </p>
    </div>

    @if(!explanationDraft.readinessNotes().isEmpty())
        <div class="cb-card mb-6">
            <h3 class="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Checklist Notes</h3>
            <ul class="space-y-1 text-sm text-gray-700">
                @for(var note : explanationDraft.readinessNotes())
                    <li>${note}</li>
                @endfor
            </ul>
        </div>
    @endif

    @if(!explanationDraft.evidenceAnchorReview().isEmpty())
        <div class="cb-card mb-6">
            <h3 class="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Evidence Anchors</h3>
            <ul class="space-y-1 text-sm text-gray-700">
                @for(var note : explanationDraft.evidenceAnchorReview())
                    <li>${note}</li>
                @endfor
            </ul>
        </div>
    @endif

    @if(!explanationDraft.narrativeSpine().isEmpty())
        <div class="cb-card mb-6">
            <h3 class="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Narrative Spine</h3>
            <ul class="space-y-1 text-sm text-gray-700">
                @for(var note : explanationDraft.narrativeSpine())
                    <li>${note}</li>
                @endfor
            </ul>
        </div>
    @endif

    @if(!explanationDraft.sourceUrls().isEmpty())
        <div class="cb-card">
            <h3 class="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Policy Sources</h3>
            <div class="flex flex-wrap gap-2 text-xs">
                @for(var url : explanationDraft.sourceUrls())
                    <a href="${url}" target="_blank" rel="noreferrer noopener" class="cb-break-anywhere inline-flex items-center px-2 py-1 rounded border border-gray-300 text-gray-700 hover:bg-gray-50">${url}</a>
                @endfor
            </div>
        </div>
    @endif

    <script>
        function copyExplanation() {
            const textarea = document.getElementById('explanation-text');
            navigator.clipboard.writeText(textarea.value);
        }

        function downloadEditedText() {
            const textarea = document.getElementById('explanation-text');
            const blob = new Blob([textarea.value], { type: 'text/plain;charset=utf-8' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = 'Dispute_Explanation_Draft_${explanationDraft.publicCaseRef()}_edited.txt';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);
        }
    </script>
`)
