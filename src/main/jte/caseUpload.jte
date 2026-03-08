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
@param Integer caseMaxFiles
@param Integer uploadedFileCount
@param Integer remainingFileSlots
@param Long uploadedSizeBytes
@param Long totalSizeLimitBytes
@param Long remainingSizeBytes
@param Integer totalPagesUsed
@param Integer stripePagesRemaining
@param Integer mastercardPagesRemaining
@param String sizeLimitLabel
@param List<String> missingRequiredEvidenceTypes
@param List<String> missingRecommendedEvidenceTypes
@param ReasonCodeChecklistService.ReasonChecklist reasonChecklist
@param String displayCaseState
@param String displayCaseStateBadgeClass

@template.layout(
    title = "Upload | " + report.caseToken(),
    robotsMeta = "noindex,nofollow,noarchive",
    caseTokenForVault = report.caseToken(),
    casePlatformForVault = report.platform().name(),
    caseStateForVault = displayCaseState,
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
    <div id="client-error-alert" class="mb-6 p-4 rounded-lg border hidden">
        <div id="client-alert-title" class="font-semibold mb-1">Selection needs attention</div>
        <div id="client-error-message" class="text-sm whitespace-pre-line"></div>
    </div>

    <div class="mb-8 flex flex-col md:flex-row md:items-start justify-between gap-4">
        <div>
            <div class="flex items-center gap-3 mb-2">
                <h1 class="text-3xl font-bold text-neutral-900 tracking-tight">Case ${report.caseToken()}</h1>
                <span class="cb-badge ${displayCaseStateBadgeClass}">
                    ${displayCaseState}
                </span>
            </div>
            <div class="flex gap-2 text-sm text-gray-500">
                <span class="font-medium text-gray-700">${report.platform()}</span> &bull;
                <span>Upload and classify evidence files</span>
            </div>
        </div>
    </div>

    <div class="border-b border-gray-200 mb-8 overflow-x-auto shrink-0">
        <nav class="-mb-px flex space-x-8" aria-label="Tabs">
            <a href="/c/${report.caseToken()}" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Dashboard</a>
            <a href="/c/${report.caseToken()}/upload" class="border-primary-600 text-primary-600 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors" aria-current="page">Upload Evidence</a>
            <a href="/c/${report.caseToken()}/explanation" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Explanation Draft</a>
            <a href="/c/${report.caseToken()}/export" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Export Pack</a>
        </nav>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4 mb-8">
        <div class="cb-card">
            <div class="text-xs uppercase tracking-wide text-gray-500 font-semibold mb-1">File Slots</div>
            <div id="budget-files" class="text-lg font-bold text-gray-900">${uploadedFileCount} / ${caseMaxFiles}</div>
            <div id="budget-files-remaining" class="text-sm text-gray-600">Remaining: ${remainingFileSlots}</div>
        </div>
        <div class="cb-card">
            <div class="text-xs uppercase tracking-wide text-gray-500 font-semibold mb-1">Total Size Budget</div>
            <div id="budget-size" class="text-lg font-bold text-gray-900">${String.format("%.2f", uploadedSizeBytes / 1048576.0)} MB / ${sizeLimitLabel}</div>
            <div id="budget-size-remaining" class="text-sm text-gray-600">Remaining: ${String.format("%.2f", remainingSizeBytes / 1048576.0)} MB</div>
        </div>
        <div class="cb-card">
            <div class="text-xs uppercase tracking-wide text-gray-500 font-semibold mb-1">Current Pages</div>
            <div class="text-lg font-bold text-gray-900">${totalPagesUsed}</div>
            <div class="text-sm text-gray-600">Across all uploaded files</div>
        </div>
        <div class="cb-card">
            @if(report.productScope().name().equals("STRIPE_DISPUTE"))
                <div class="text-xs uppercase tracking-wide text-gray-500 font-semibold mb-1">Stripe Page Headroom</div>
                <div class="text-lg font-bold text-gray-900">+${stripePagesRemaining} before 50-page block</div>
                @if(mastercardPagesRemaining >= 0)
                    <div class="text-sm text-gray-600">Mastercard extra pages left: ${mastercardPagesRemaining}</div>
                @else
                    <div class="text-sm text-gray-600">Card-specific limit applies only to Mastercard</div>
                @endif
            @else
                <div class="text-xs uppercase tracking-wide text-gray-500 font-semibold mb-1">Scope Rule</div>
                <div class="text-lg font-bold text-gray-900">Per-platform limits active</div>
                @if(report.productScope().name().equals("SHOPIFY_PAYMENTS_CHARGEBACK"))
                    <div class="text-sm text-gray-600">Each file &lt;= 2MB, total &lt;= 4MB.</div>
                @else
                    <div class="text-sm text-gray-600">Total &lt;= 4.5MB, each PDF &lt; 50 pages.</div>
                @endif
            @endif
        </div>
    </div>

    @if(report.productScope().name().equals("STRIPE_DISPUTE"))
        <div class="mb-8 p-4 rounded-xl border border-blue-200 bg-blue-50 text-blue-950">
            <div class="font-semibold mb-1">Stripe evidence size note</div>
            <p class="text-sm leading-6">
                Some Stripe File Upload API references mention 5MB uploads, but dispute evidence guidance is stricter at a 4.5MB combined pack limit.
                This workflow validates against the safer 4.5MB rule so you do not clean a pack that still fails in the dispute dashboard.
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

    <div class="mb-8 rounded-xl border border-sky-200 bg-sky-50 p-5 text-sky-950">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div>
                <h2 class="text-sm font-semibold uppercase tracking-wide">How upload works</h2>
                <ol class="mt-3 space-y-2 pl-5 text-sm leading-6 list-decimal">
                    <li>Click <strong>Browse Files</strong> or drag files here. Nothing uploads until you confirm in the mapping modal.</li>
                    <li>Review the guessed evidence type for each file and change it if needed.</li>
                    <li>Unsupported but decodable image files can be auto-converted to <strong>JPEG</strong> during upload before validation. HEIC / HEIF from iPhone Photos is converted to JPEG in your browser before upload, but AVIF still needs manual conversion to JPG or PNG first.</li>
                    <li>Click <strong>Upload Files</strong>. Validation starts automatically after each confirmed upload.</li>
                    <li>Format checks can pass before export is ready. Export review stays locked until required evidence is complete.</li>
                </ol>
            </div>
            @if(reasonChecklist.hasReasonCode() && !reasonChecklist.missingRequiredEvidence().isEmpty())
                <div class="rounded-lg border border-sky-300 bg-white/80 p-4 text-sm shadow-sm lg:max-w-sm">
                    <div class="font-semibold text-sky-950">Still needed for this reason</div>
                    <div class="mt-2 text-sky-900">${String.join(", ", reasonChecklist.missingRequiredEvidence())}</div>
                </div>
            @endif
        </div>
    </div>

    <div class="cb-card mb-8">
        <div class="flex flex-col md:flex-row md:items-start md:justify-between gap-4 mb-4">
            <div>
                <h2 class="text-sm font-semibold text-gray-700 uppercase tracking-wide">Reason-Code Checklist</h2>
                <p class="text-xs text-gray-500 mt-1">Upload against reason-specific evidence requirements.</p>
            </div>
            @if(reasonChecklist.reasonInput() != null && !reasonChecklist.reasonInput().isBlank())
                <div class="text-xs text-gray-500">
                    Input: <code class="bg-gray-100 px-1.5 py-0.5 rounded">${reasonChecklist.reasonInput()}</code>
                </div>
            @endif
        </div>

        @if(!reasonChecklist.hasReasonCode())
            <div class="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg p-3">
                Reason code is not set. Create the case with a reason code to unlock reason-specific guidance.
            </div>
        @else
            <div class="mb-4 flex items-center gap-2 flex-wrap">
                <span class="cb-badge cb-badge-blue">${reasonChecklist.reasonLabel()}</span>
                <span class="text-xs text-gray-500">Canonical key: <code class="bg-gray-100 px-1 rounded">${reasonChecklist.canonicalReasonKey()}</code></span>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                    <h3 class="text-xs font-semibold text-gray-700 uppercase tracking-wide mb-2">Required</h3>
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
                </div>
                <div>
                    <h3 class="text-xs font-semibold text-gray-700 uppercase tracking-wide mb-2">Recommended</h3>
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
                </div>
            </div>
        @endif
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div class="lg:col-span-1 border border-dashed border-gray-300 rounded-xl bg-gray-50 flex flex-col items-center justify-center p-8 text-center"
             id="dropzone"
             style="min-height: 400px; transition: all 0.2s;">
            <svg class="w-16 h-16 text-primary-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path></svg>
            <h3 class="text-xl font-bold text-gray-900 mb-2">Drag & Drop files here</h3>
            <p class="text-gray-500 text-sm mb-6 max-w-xs">After you choose files, a mapping modal opens first. Upload starts only after you confirm.</p>

            <input type="file" id="file-input" multiple class="hidden" accept=".pdf,.png,.jpg,.jpeg,.gif,.bmp,.webp,.heic,.heif,.avif,image/*">
            <button onclick="document.getElementById('file-input').click()" class="cb-btn cb-btn-primary shadow-sm">
                Browse Files
            </button>

            <div id="upload-progress-container" class="w-full mt-6 hidden">
                <div class="text-xs font-semibold text-gray-600 mb-1 text-left" id="upload-status-text">Uploading...</div>
                <div class="w-full bg-gray-200 rounded-full h-2">
                    <div class="bg-primary-600 h-2 rounded-full" id="upload-progress-bar" style="width: 0%"></div>
                </div>
            </div>
        </div>

        <div class="lg:col-span-2 flex flex-col gap-6">
            <div class="cb-card h-full flex flex-col">
                <div class="flex justify-between items-center mb-6 border-b border-gray-100 pb-4">
                    <h2 class="text-lg font-bold text-gray-900">Your Evidence Files</h2>
                    <span class="text-sm font-medium text-gray-500"><span id="file-count">${uploadedFileCount}</span> files uploaded</span>
                </div>

                <div id="file-list-container" class="flex-1 overflow-y-auto max-h-[400px]">
                    @if(report.files().isEmpty())
                        <div id="empty-state" class="h-full flex flex-col items-center justify-center text-gray-400 py-12">
                            <p>No files uploaded yet.</p>
                        </div>
                    @else
                        <div class="space-y-3" id="files-list">
                            @for(var file : report.files())
                                <div class="p-4 border border-gray-200 rounded-lg flex items-center justify-between bg-white hover:border-primary-300 transition-colors">
                                    <div class="flex items-center gap-3">
                                        <div class="w-10 h-10 rounded bg-gray-100 flex items-center justify-center text-gray-500 font-bold text-xs uppercase">
                                            ${file.fileFormat()}
                                        </div>
                                        <div>
                                            <div class="font-semibold text-gray-900 flex items-center gap-2">
                                                ${file.evidenceType()}
                                                <span class="text-xs px-2 py-0.5 rounded bg-gray-100 text-gray-600 font-mono">${String.format("%.1f", file.sizeBytes() / 1024.0)} KB</span>
                                            </div>
                                            <div class="text-xs text-gray-500 mt-1">Pages: ${file.pageCount()}</div>
                                        </div>
                                    </div>
                                    <div class="text-green-600">
                                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>
                                    </div>
                                </div>
                            @endfor
                        </div>
                    @endif
                </div>
            </div>

            <div class="cb-card bg-slate-900 text-white rounded-xl shadow-lg relative overflow-hidden">
                <div class="absolute top-0 right-0 -mt-20 -mr-20 w-64 h-64 bg-primary-600 rounded-full blur-[80px] opacity-30 pointer-events-none"></div>
                <div class="relative z-10 flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
                    <div>
                        <div class="flex items-center gap-2 mb-2">
                            <div class="w-2 h-2 rounded-full bg-green-400 animate-pulse"></div>
                            <h3 class="font-bold text-white text-lg tracking-wide">Validation Engine</h3>
                        </div>
                        <p class="text-slate-300 text-sm max-w-sm">Validation runs automatically after you confirm uploads. Upload can auto-convert decodable image formats like GIF, BMP, and WEBP after transfer. iPhone HEIC / HEIF is converted to JPEG in your browser before upload. AVIF still needs manual conversion before upload. Auto-fix handles the remaining supported blockers after validation.</p>
                    </div>
                    <div class="flex flex-col gap-2 shrink-0">
                        <form method="post" action="/c/${report.caseToken()}/validate">
                            <button type="submit" class="w-full cb-btn bg-slate-800 border-slate-700 text-white hover:bg-slate-700 hover:border-slate-600 transition-colors shadow-sm">
                                Re-run Validation
                            </button>
                        </form>
                        <form method="post" action="/c/${report.caseToken()}/fix">
                            <button type="submit" class="w-full cb-btn bg-primary-600 hover:bg-primary-500 border-primary-500 text-white shadow-sm flex items-center justify-center gap-2 transition-colors">
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path></svg>
                                Auto-Fix Formats
                            </button>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div id="mapping-modal" class="fixed inset-0 bg-black/50 hidden items-center justify-center z-50 p-4">
        <div class="bg-white rounded-xl shadow-xl w-full max-w-3xl max-h-[90vh] overflow-hidden">
            <div class="p-5 border-b border-gray-200 flex items-center justify-between">
                <div>
                    <h3 class="text-lg font-bold text-gray-900">Map Files to Evidence Types</h3>
                    <p class="text-sm text-gray-500 mt-1">Set the right type for each file before upload.</p>
                </div>
                <button id="mapping-close-btn" type="button" class="cb-btn cb-btn-secondary px-3 py-2 text-xs">Close</button>
            </div>
            <div class="p-5">
                <div class="text-sm text-gray-600 mb-3"><span id="mapping-count">0</span> file(s) selected</div>
                <div id="mapping-analysis-status" class="mb-3 text-xs text-gray-500"></div>
                <div id="mapping-list" class="space-y-3 max-h-[48vh] overflow-y-auto"></div>
            </div>
            <div class="p-5 border-t border-gray-200 flex items-center justify-end gap-2">
                <button id="mapping-cancel-btn" type="button" class="cb-btn cb-btn-secondary">Cancel</button>
                <button id="mapping-start-upload-btn" type="button" class="cb-btn cb-btn-primary">Upload Files</button>
            </div>
        </div>
    </div>

    <template id="evidence-type-select-template">
        <select class="cb-input text-sm evidence-type-select min-w-[260px]">
            @for(EvidenceType type : evidenceTypes)
                <option value="${type.name()}">${type.name()}</option>
            @endfor
        </select>
    </template>

    <script src="/js/vendor/heic2any.min.js"></script>
    <script>
        const dropzone = document.getElementById('dropzone');
        const fileInput = document.getElementById('file-input');
        const progressBar = document.getElementById('upload-progress-bar');
        const progressContainer = document.getElementById('upload-progress-container');
        const statusText = document.getElementById('upload-status-text');
        const mappingModal = document.getElementById('mapping-modal');
        const mappingList = document.getElementById('mapping-list');
        const mappingCount = document.getElementById('mapping-count');
        const mappingAnalysisStatus = document.getElementById('mapping-analysis-status');
        const mappingStartUploadBtn = document.getElementById('mapping-start-upload-btn');
        const mappingCancelBtn = document.getElementById('mapping-cancel-btn');
        const mappingCloseBtn = document.getElementById('mapping-close-btn');
        const evidenceTypeSelectTemplate = document.getElementById('evidence-type-select-template');
        const clientErrorAlert = document.getElementById('client-error-alert');
        const clientAlertTitle = document.getElementById('client-alert-title');
        const clientErrorMessage = document.getElementById('client-error-message');
        const fileCountElement = document.getElementById('file-count');
        const budgetFilesElement = document.getElementById('budget-files');
        const budgetFilesRemainingElement = document.getElementById('budget-files-remaining');
        const budgetSizeElement = document.getElementById('budget-size');
        const budgetSizeRemainingElement = document.getElementById('budget-size-remaining');
        const caseId = '${report.caseId().toString()}';
        const caseToken = '${report.caseToken()}';
        const caseMaxFiles = ${caseMaxFiles};
        let uploadedFileCount = ${uploadedFileCount};
        let currentTotalSizeBytes = ${uploadedSizeBytes};
        const totalSizeLimitBytes = ${totalSizeLimitBytes};
        const defaultUploadBtnText = mappingStartUploadBtn.textContent;
        const missingRequiredEvidenceTypes = [
            @for(String type : missingRequiredEvidenceTypes)
                '${type}',
            @endfor
        ];
        const missingRecommendedEvidenceTypes = [
            @for(String type : missingRecommendedEvidenceTypes)
                '${type}',
            @endfor
        ];
        const existingEvidenceTypes = [
            @for(var file : report.files())
                '${file.evidenceType().name()}',
            @endfor
        ];

        let pendingFiles = [];
        let remoteSuggestionCache = new Map();
        let suggestionAnalysisRun = 0;

        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            dropzone.addEventListener(eventName, preventDefaults, false);
            document.body.addEventListener(eventName, preventDefaults, false);
        });

        function preventDefaults(e) {
            e.preventDefault();
            e.stopPropagation();
        }

        ['dragenter', 'dragover'].forEach(eventName => {
            dropzone.addEventListener(eventName, () => {
                dropzone.classList.add('border-primary-500', 'bg-primary-50');
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropzone.addEventListener(eventName, () => {
                dropzone.classList.remove('border-primary-500', 'bg-primary-50');
            }, false);
        });

        dropzone.addEventListener('drop', (e) => {
            void handleFiles(e.dataTransfer.files);
        });

        fileInput.addEventListener('change', function() {
            void handleFiles(this.files);
            this.value = '';
        });

        mappingCloseBtn.addEventListener('click', () => closeMappingModal(true));
        mappingCancelBtn.addEventListener('click', () => closeMappingModal(true));
        mappingModal.addEventListener('click', (e) => {
            if (e.target === mappingModal) {
                closeMappingModal(true);
            }
        });
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && !mappingModal.classList.contains('hidden')) {
                closeMappingModal(true);
            }
        });

        mappingStartUploadBtn.addEventListener('click', async () => {
            if (pendingFiles.length === 0) {
                return;
            }
            mappingStartUploadBtn.disabled = true;
            mappingStartUploadBtn.textContent = 'Uploading...';

            const rows = mappingList.querySelectorAll('[data-row-index]');
            const mappings = Array.from(rows).map((row) => {
                const index = Number(row.getAttribute('data-row-index'));
                const select = row.querySelector('.evidence-type-select');
                return {
                    file: pendingFiles[index],
                    evidenceType: select.value
                };
            });

            try {
                closeMappingModal(false);
                await uploadMappedFiles(mappings);
            } finally {
                clearPendingFiles();
                mappingStartUploadBtn.disabled = false;
                mappingStartUploadBtn.textContent = defaultUploadBtnText;
            }
        });

        async function handleFiles(fileList) {
            if (!fileList || fileList.length === 0) {
                return;
            }
            hideClientError();
            const errors = [];
            const preparedSelection = await convertHeicSelections(Array.from(fileList));
            const selectedFiles = preparedSelection.files;
            const accepted = [];
            const rejected = [];
            const avifRejected = [];
            for (const file of selectedFiles) {
                if (isAvifSelection(file)) {
                    avifRejected.push(file.name);
                } else if (isDirectUploadFile(file) || isConvertibleImageSelection(file)) {
                    accepted.push(file);
                } else {
                    rejected.push(file.name);
                }
            }
            const browserConvertedHeic = selectedFiles.filter((file) => file._convertedFromHeic);
            if (browserConvertedHeic.length > 0) {
                errors.push(
                    'HEIC / HEIF files were converted locally in your browser to JPEG before upload:\n- '
                    + browserConvertedHeic.map((file) => (file._sourceName || file.name) + ' -> ' + file.name).join('\n- ')
                );
            }
            if (preparedSelection.failed.length > 0) {
                errors.push(
                    'HEIC / HEIF conversion failed in this browser. Convert these files to JPG or PNG before upload:\n- '
                    + preparedSelection.failed.join('\n- ')
                );
            }
            if (avifRejected.length > 0) {
                errors.push('AVIF is not supported in this upload pipeline yet. Convert these files to JPG or PNG before upload:\n- ' + avifRejected.join('\n- '));
            }
            if (rejected.length > 0) {
                errors.push('Unsupported file type (direct upload: PDF, PNG, JPG, JPEG; image auto-convert attempts for GIF, BMP, WEBP, and other browser-recognized images; HEIC / HEIF should convert in-browser first):\n- ' + rejected.join('\n- '));
            }
            if (accepted.length === 0) {
                if (errors.length > 0) {
                    showClientAlert('warning', 'Selection needs attention', errors.join('\n\n'));
                }
                return;
            }

            const remainingSlots = caseMaxFiles - uploadedFileCount;
            if (remainingSlots <= 0) {
                errors.push('Case file limit reached (' + caseMaxFiles + '). Delete or merge files before adding more.');
                showClientAlert('warning', 'Selection needs attention', errors.join('\n\n'));
                return;
            }

            let cappedAccepted = accepted;
            if (accepted.length > remainingSlots) {
                cappedAccepted = accepted.slice(0, remainingSlots);
                const dropped = accepted.slice(remainingSlots).map((file) => file.name);
                errors.push(
                    'Only ' + remainingSlots + ' more file(s) can be added now. Skipped:\n- ' + dropped.join('\n- ')
                );
            }

            const selectedBytes = cappedAccepted.reduce((sum, file) => sum + file.size, 0);
            if (currentTotalSizeBytes + selectedBytes > totalSizeLimitBytes) {
                errors.push(
                    'This selection exceeds your current policy total size budget ('
                    + formatBytes(totalSizeLimitBytes)
                    + '). Validation will fail until files are removed or compressed.'
                );
            }

            if (errors.length > 0) {
                showClientAlert('warning', 'Selection needs attention', errors.join('\n\n'));
            }

            setPendingFiles(cappedAccepted);
            if (pendingFiles.length === 0) {
                return;
            }
            renderMappingRows();
            openMappingModal();
            requestPreviewSuggestions();
        }

        function openMappingModal() {
            mappingModal.classList.remove('hidden');
            mappingModal.classList.add('flex');
        }

        function closeMappingModal(clearSelection) {
            mappingModal.classList.remove('flex');
            mappingModal.classList.add('hidden');
            if (clearSelection) {
                clearPendingFiles();
            }
        }

        function renderMappingRows() {
            mappingList.innerHTML = '';
            mappingCount.textContent = pendingFiles.length.toString();
            const suggestionPlan = buildSuggestionPlan(pendingFiles);
            pendingFiles.forEach((file, index) => {
                const row = document.createElement('div');
                row.className = 'border border-gray-200 rounded-lg p-3 flex flex-col md:flex-row md:items-start gap-3 justify-between';
                row.setAttribute('data-row-index', index.toString());

                const left = document.createElement('div');
                left.className = 'flex items-start gap-3 min-w-0 flex-1';

                const preview = buildPreviewTile(file);
                const info = document.createElement('div');
                info.className = 'min-w-0 flex-1';
                const kb = (file.size / 1024).toFixed(1);
                const autoConvertBadge = file._convertedFromHeic
                    ? '<span class="inline-flex items-center rounded-full border border-sky-200 bg-sky-50 px-2 py-0.5 text-[11px] font-semibold text-sky-900">Browser HEIC -> JPEG</span>'
                    : isConvertibleImageSelection(file)
                        ? '<span class="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-900">Auto-convert to JPEG</span>'
                        : '';
                const suggestion = suggestionPlan.get(index) || fallbackSuggestion();
                const suggestionBadge = suggestion.source === 'content'
                    ? '<span class="inline-flex items-center rounded-full border border-indigo-200 bg-indigo-50 px-2 py-0.5 text-[11px] font-semibold text-indigo-900">Content match</span>'
                    : suggestion.source === 'ocr'
                        ? '<span class="inline-flex items-center rounded-full border border-fuchsia-200 bg-fuchsia-50 px-2 py-0.5 text-[11px] font-semibold text-fuchsia-900">OCR text match</span>'
                    : suggestion.source === 'metadata'
                        ? '<span class="inline-flex items-center rounded-full border border-cyan-200 bg-cyan-50 px-2 py-0.5 text-[11px] font-semibold text-cyan-900">Preview signal</span>'
                        : suggestion.source === 'required'
                            ? '<span class="inline-flex items-center rounded-full border border-sky-200 bg-sky-50 px-2 py-0.5 text-[11px] font-semibold text-sky-900">Needed for this case</span>'
                            : suggestion.source === 'recommended'
                                ? '<span class="inline-flex items-center rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-900">Recommended next</span>'
                                : suggestion.source === 'keyword'
                                    ? '<span class="inline-flex items-center rounded-full border border-violet-200 bg-violet-50 px-2 py-0.5 text-[11px] font-semibold text-violet-900">Name match</span>'
                                    : '<span class="inline-flex items-center rounded-full border border-gray-200 bg-gray-50 px-2 py-0.5 text-[11px] font-semibold text-gray-700">Fallback</span>';
                const sourceHint = file._convertedFromHeic
                    ? 'Converted locally from ' + (file._sourceName || 'HEIC / HEIF') + ' to ' + file.name + ' before upload'
                    : isConvertibleImageSelection(file)
                        ? 'Uploaded as image, stored as JPEG after conversion'
                        : kb + ' KB';
                const displayName = file._sourceName || file.name;
                info.innerHTML =
                    '<div class="font-medium text-gray-900 truncate flex items-center gap-2 flex-wrap">' + escapeHtml(displayName) + autoConvertBadge + suggestionBadge + '</div>' +
                    '<div class="text-xs text-gray-500 mt-1">' + escapeHtml(sourceHint) + '</div>' +
                    '<div class="mt-2 text-xs text-sky-900">Suggested: <strong>' + escapeHtml(humanizeEvidenceTypeName(suggestion.evidenceType)) + '</strong> - ' + escapeHtml(suggestion.reason) + '</div>';
                left.appendChild(preview);
                left.appendChild(info);

                const right = document.createElement('div');
                right.className = 'flex items-center gap-2 md:pl-4';

                const select = evidenceTypeSelectTemplate.content.firstElementChild.cloneNode(true);
                select.value = file._selectedEvidenceType || suggestion.evidenceType;
                select.addEventListener('change', () => {
                    file._selectedEvidenceType = select.value;
                });
                right.appendChild(select);

                const removeBtn = document.createElement('button');
                removeBtn.type = 'button';
                removeBtn.className = 'cb-btn cb-btn-secondary text-xs px-3 py-2';
                removeBtn.textContent = 'Remove';
                removeBtn.addEventListener('click', () => {
                    setPendingFiles(pendingFiles.filter((_, i) => i !== index));
                    renderMappingRows();
                    requestPreviewSuggestions();
                });
                right.appendChild(removeBtn);

                row.appendChild(left);
                row.appendChild(right);
                mappingList.appendChild(row);
            });

            mappingStartUploadBtn.disabled = pendingFiles.length === 0;
        }

        function buildSuggestionPlan(files) {
            const plan = new Map();
            const coveredTypes = new Set(existingEvidenceTypes);
            const genericIndexes = [];

            files.forEach((file, index) => {
                const suggestion = remoteSuggestionCache.get(index) || detectEvidenceSuggestion(file, files);
                plan.set(index, suggestion);
                if (suggestion.source === 'fallback') {
                    genericIndexes.push(index);
                    return;
                }
                coveredTypes.add(suggestion.evidenceType);
            });

            const remainingRequired = missingRequiredEvidenceTypes.filter((type) => !coveredTypes.has(type));
            const remainingRecommended = missingRecommendedEvidenceTypes.filter(
                (type) => !coveredTypes.has(type) && !remainingRequired.includes(type)
            );

            genericIndexes.forEach((index) => {
                const file = files[index];
                const current = plan.get(index);
                if (!isGapFillCandidate(file, files)) {
                    if (!current) {
                        plan.set(index, fallbackSuggestion());
                    }
                    return;
                }
                if (remainingRequired.length > 0) {
                    const type = remainingRequired.shift();
                    plan.set(index, {
                        evidenceType: type,
                        reason: 'This case still needs this evidence type.',
                        source: 'required'
                    });
                    return;
                }
                if (remainingRecommended.length > 0) {
                    const type = remainingRecommended.shift();
                    plan.set(index, {
                        evidenceType: type,
                        reason: 'This case would be stronger with this evidence type.',
                        source: 'recommended'
                    });
                    return;
                }
                if (!current) {
                    plan.set(index, fallbackSuggestion());
                }
            });

            return plan;
        }

        async function requestPreviewSuggestions() {
            suggestionAnalysisRun += 1;
            const runId = suggestionAnalysisRun;
            remoteSuggestionCache = new Map();

            if (pendingFiles.length === 0) {
                mappingAnalysisStatus.textContent = '';
                renderMappingRows();
                return;
            }

            mappingAnalysisStatus.textContent = 'Analyzing preview text and metadata...';
            renderMappingRows();

            const formData = new FormData();
            pendingFiles.forEach((file) => formData.append('files', file, file.name));

            try {
                const response = await fetch('/api/cases/' + caseId + '/file-suggestions', {
                    method: 'POST',
                    headers: {
                        'X-Case-Token': caseToken
                    },
                    body: formData
                });
                if (runId !== suggestionAnalysisRun) {
                    return;
                }
                if (!response.ok) {
                    mappingAnalysisStatus.textContent = 'Preview analysis is unavailable right now. Using filename-based suggestions.';
                    return;
                }

                const suggestions = await response.json();
                remoteSuggestionCache = new Map(
                    suggestions.map((suggestion) => [Number(suggestion.index), suggestion])
                );
                const strongCount = suggestions.filter((suggestion) =>
                    suggestion.source === 'content' || suggestion.source === 'ocr' || suggestion.source === 'metadata'
                ).length;
                mappingAnalysisStatus.textContent = strongCount > 0
                    ? 'Preview analysis found ' + strongCount + ' stronger content, OCR, or metadata matches.'
                    : 'Preview analysis finished. No stronger content clues were found.';
                renderMappingRows();
            } catch (_) {
                if (runId !== suggestionAnalysisRun) {
                    return;
                }
                mappingAnalysisStatus.textContent = 'Preview analysis is unavailable right now. Using filename-based suggestions.';
            }
        }

        function detectEvidenceSuggestion(file, files) {
            const lower = (file.name || '').toLowerCase();
            const type = (file.type || '').toLowerCase();
            if (matchesAny(lower, ['policy', 'terms', 'agreement', 'contract', 'conditions'])) {
                return { evidenceType: 'POLICIES', reason: 'Filename looks like terms or policy evidence.', source: 'keyword' };
            }
            if (matchesAny(lower, ['receipt', 'invoice', 'order', 'purchase', 'checkout', 'transaction', 'statement'])) {
                return { evidenceType: 'ORDER_RECEIPT', reason: 'Filename looks like receipt or order evidence.', source: 'keyword' };
            }
            if (matchesAny(lower, ['tracking', 'delivery', 'shipment', 'shipping', 'fulfillment', 'carrier', 'fedex', 'ups', 'dhl', 'usps'])) {
                return { evidenceType: 'FULFILLMENT_DELIVERY', reason: 'Filename looks like delivery or tracking proof.', source: 'keyword' };
            }
            if (matchesAny(lower, ['message', 'chat', 'conversation', 'email', 'communication', 'whatsapp', 'telegram', 'support'])) {
                return { evidenceType: 'CUSTOMER_COMMUNICATION', reason: 'Filename looks like customer communication.', source: 'keyword' };
            }
            if (matchesAny(lower, ['refund', 'cancel', 'cancellation', 'reversal', 'credit note', 'credit_note'])) {
                return { evidenceType: 'REFUND_CANCELLATION', reason: 'Filename looks like refund or cancellation proof.', source: 'keyword' };
            }
            if (matchesAny(lower, ['customer', 'address', 'profile', 'billing_profile', 'account', 'identity'])) {
                return { evidenceType: 'CUSTOMER_DETAILS', reason: 'Filename looks like customer identity or billing details.', source: 'keyword' };
            }
            if (matchesAny(lower, ['usage', 'log', 'access', 'session', 'device', 'download', 'ip', 'activity', 'digital'])) {
                return { evidenceType: 'DIGITAL_USAGE_LOGS', reason: 'Filename looks like digital usage or access logs.', source: 'keyword' };
            }
            if (looksLikeScreenshotBatch(file, files) && missingRequiredEvidenceTypes.includes('CUSTOMER_COMMUNICATION')) {
                return { evidenceType: 'CUSTOMER_COMMUNICATION', reason: 'Screenshot-style images often map to customer communication in this case.', source: 'recommended' };
            }
            return fallbackSuggestion();
        }

        function fallbackSuggestion() {
            return {
                evidenceType: 'OTHER_SUPPORTING',
                reason: 'No strong clue detected yet. Use the preview to confirm the right type.',
                source: 'fallback'
            };
        }

        function matchesAny(text, keywords) {
            return keywords.some((keyword) => text.includes(keyword));
        }

        function looksLikeScreenshotBatch(file, files) {
            const lower = (file.name || '').toLowerCase();
            const type = (file.type || '').toLowerCase();
            const genericName = /^((img|image|photo|scan|screenshot|document)[-_ ]?\d+|document[_-]?\d+)\./i.test(file.name || '');
            const imageCount = files.filter((entry) => (entry.type || '').toLowerCase().startsWith('image/')).length;
            return imageCount >= 2
                && type.startsWith('image/')
                && (genericName || lower.includes('screen') || lower.includes('img_'));
        }

        function isGapFillCandidate(file, files) {
            const lower = (file.name || '').toLowerCase();
            const type = (file.type || '').toLowerCase();
            return looksLikeScreenshotBatch(file, files)
                || type === 'application/pdf'
                || lower.endsWith('.pdf');
        }

        function humanizeEvidenceTypeName(name) {
            switch (name) {
                case 'ORDER_RECEIPT':
                    return 'Order receipt';
                case 'CUSTOMER_DETAILS':
                    return 'Customer details';
                case 'CUSTOMER_COMMUNICATION':
                    return 'Customer communication';
                case 'POLICIES':
                    return 'Policies';
                case 'FULFILLMENT_DELIVERY':
                    return 'Fulfillment / delivery proof';
                case 'DIGITAL_USAGE_LOGS':
                    return 'Digital usage logs';
                case 'REFUND_CANCELLATION':
                    return 'Refund / cancellation';
                default:
                    return 'Other supporting';
            }
        }

        function isDirectUploadFile(file) {
            const lower = (file.name || '').toLowerCase();
            const type = (file.type || '').toLowerCase();
            return lower.endsWith('.pdf')
                || lower.endsWith('.png')
                || lower.endsWith('.jpg')
                || lower.endsWith('.jpeg')
                || type === 'application/pdf'
                || type === 'image/png'
                || type === 'image/jpeg';
        }

        function isConvertibleImageSelection(file) {
            if (isDirectUploadFile(file)) {
                return false;
            }
            if (isAvifSelection(file)) {
                return false;
            }
            const lower = (file.name || '').toLowerCase();
            const type = (file.type || '').toLowerCase();
            return type.startsWith('image/')
                || lower.endsWith('.gif')
                || lower.endsWith('.bmp')
                || lower.endsWith('.webp');
        }

        function isHeicSelection(file) {
            const lower = (file.name || '').toLowerCase();
            const type = (file.type || '').toLowerCase();
            return lower.endsWith('.heic')
                || lower.endsWith('.heif')
                || type === 'image/heic'
                || type === 'image/heif'
                || type === 'image/heic-sequence'
                || type === 'image/heif-sequence';
        }

        function isAvifSelection(file) {
            const lower = (file.name || '').toLowerCase();
            const type = (file.type || '').toLowerCase();
            return lower.endsWith('.avif') || type === 'image/avif';
        }

        function buildPreviewTile(file) {
            const wrapper = document.createElement('div');
            wrapper.className = 'h-24 w-24 shrink-0 overflow-hidden rounded-lg border border-gray-200 bg-gray-50';
            const lower = (file.name || '').toLowerCase();
            const type = (file.type || '').toLowerCase();

            if (isHeicSelection(file)) {
                const fallback = document.createElement('div');
                fallback.className = 'flex h-full w-full items-center justify-center text-center text-[11px] font-semibold uppercase tracking-wide text-sky-900 bg-sky-50';
                fallback.textContent = 'HEIC';
                wrapper.appendChild(fallback);
                return wrapper;
            }

            if (type.startsWith('image/')) {
                const image = document.createElement('img');
                image.src = ensurePreviewUrl(file);
                image.alt = file.name || 'Image preview';
                image.className = 'h-full w-full object-cover';
                wrapper.appendChild(image);
                return wrapper;
            }

            if (lower.endsWith('.pdf')) {
                const frame = document.createElement('iframe');
                frame.src = ensurePreviewUrl(file) + '#toolbar=0&navpanes=0&scrollbar=0&page=1&view=FitH';
                frame.title = 'PDF preview';
                frame.className = 'h-full w-full bg-white';
                wrapper.appendChild(frame);
                return wrapper;
            }

            const fallback = document.createElement('div');
            fallback.className = 'flex h-full w-full items-center justify-center text-center text-[11px] font-semibold uppercase tracking-wide text-gray-500';
            fallback.textContent = lower.endsWith('.pdf') ? 'PDF' : 'Preview';
            wrapper.appendChild(fallback);
            return wrapper;
        }

        function ensurePreviewUrl(file) {
            if (!file._previewUrl) {
                file._previewUrl = URL.createObjectURL(file);
            }
            return file._previewUrl;
        }

        async function convertHeicSelections(files) {
            const prepared = [];
            const failed = [];
            for (const file of files) {
                if (!isHeicSelection(file)) {
                    prepared.push(file);
                    continue;
                }
                try {
                    prepared.push(await convertHeicFileToJpeg(file));
                } catch (_) {
                    failed.push(file.name);
                }
            }
            return { files: prepared, failed };
        }

        async function convertHeicFileToJpeg(file) {
            if (typeof window.heic2any !== 'function') {
                throw new Error('heic2any unavailable');
            }
            const converted = await window.heic2any({
                blob: file,
                toType: 'image/jpeg',
                quality: 0.92
            });
            const convertedBlob = Array.isArray(converted) ? converted[0] : converted;
            if (!(convertedBlob instanceof Blob) || convertedBlob.size === 0) {
                throw new Error('empty converted blob');
            }
            const convertedFile = new File(
                [convertedBlob],
                convertedJpegName(file.name || 'upload.heic'),
                {
                    type: 'image/jpeg',
                    lastModified: file.lastModified || Date.now()
                }
            );
            convertedFile._convertedFromHeic = true;
            convertedFile._sourceName = file.name || 'upload.heic';
            convertedFile._sourceContentType = file.type || '';
            return convertedFile;
        }

        function convertedJpegName(name) {
            const base = (name || 'upload').replace(/\.[^.]+$/, '');
            return (base || 'upload') + '_autoconvert.jpg';
        }

        function setPendingFiles(nextFiles) {
            const nextSet = new Set(nextFiles);
            pendingFiles.forEach((file) => {
                if (!nextSet.has(file) && file._previewUrl) {
                    URL.revokeObjectURL(file._previewUrl);
                    delete file._previewUrl;
                }
            });
            pendingFiles = nextFiles;
        }

        function clearPendingFiles() {
            suggestionAnalysisRun += 1;
            remoteSuggestionCache = new Map();
            pendingFiles.forEach((file) => {
                if (file._previewUrl) {
                    URL.revokeObjectURL(file._previewUrl);
                    delete file._previewUrl;
                }
                delete file._selectedEvidenceType;
            });
            pendingFiles = [];
            mappingList.innerHTML = '';
            mappingCount.textContent = '0';
            mappingAnalysisStatus.textContent = '';
        }

        async function uploadMappedFiles(mappings) {
            hideClientError();
            progressContainer.classList.remove('hidden');
            progressBar.style.width = '0%';
            statusText.innerText = 'Uploading...';

            let completed = 0;
            const failures = [];

            for (const mapping of mappings) {
                statusText.innerText = 'Uploading ' + (completed + 1) + ' of ' + mappings.length + '...';
                const uploadResult = await uploadSingleFileWithRetry(mapping, 3);
                if (!uploadResult.ok) {
                    failures.push(mapping.file.name + ': ' + uploadResult.reason);
                } else {
                    uploadedFileCount += 1;
                    currentTotalSizeBytes += mapping.file.size;
                }

                completed++;
                progressBar.style.width = ((completed / mappings.length) * 100) + '%';
            }

            updateBudgetIndicators();

            if (failures.length > 0) {
                showClientAlert('danger', 'Upload failed', 'Some files failed to upload:\n- ' + failures.join('\n- '));
                statusText.innerText = 'Upload failed for ' + failures.length + ' file(s).';
                return;
            }

            statusText.innerText = 'Running validation...';
            try {
                const validateResponse = await fetch('/api/cases/' + caseId + '/validate-stored', {
                    method: 'POST',
                    headers: {
                        'X-Case-Token': caseToken,
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ earlySubmit: false })
                });

                if (!validateResponse.ok) {
                    showClientAlert('danger', 'Validation request failed', 'Files uploaded, but validation failed. Please run manual check.');
                    statusText.innerText = 'Validation request failed.';
                    return;
                }
            } catch (error) {
                showClientAlert('danger', 'Validation request failed', 'Files uploaded, but validation request failed. Please run manual check.');
                statusText.innerText = 'Validation request failed.';
                return;
            }

            window.location.href = '/c/' + caseToken + '/validate?message=' + encodeURIComponent('Files uploaded and validation completed.');
        }

        async function uploadSingleFileWithRetry(mapping, maxAttempts) {
            let attempt = 0;
            while (attempt < maxAttempts) {
                attempt++;
                const formData = new FormData();
                formData.append('file', mapping.file);

                try {
                    const controller = new AbortController();
                    const timeout = setTimeout(() => controller.abort(), 45000);
                    const response = await fetch('/api/cases/' + caseId + '/files?evidenceType=' + encodeURIComponent(mapping.evidenceType), {
                        method: 'POST',
                        headers: { 'X-Case-Token': caseToken },
                        body: formData,
                        signal: controller.signal
                    });
                    clearTimeout(timeout);

                    if (response.ok) {
                        return { ok: true };
                    }

                    let reason = 'Upload failed';
                    try {
                        const json = await response.json();
                        if (json && json.error) {
                            reason = json.error;
                        }
                    } catch (_) {}

                    const retryable = response.status >= 500 || response.status === 429;
                    if (!retryable || attempt >= maxAttempts) {
                        return { ok: false, reason: reason + ' (status ' + response.status + ')' };
                    }
                } catch (error) {
                    if (attempt >= maxAttempts) {
                        const reason = error && error.name === 'AbortError'
                            ? 'Request timed out'
                            : 'Network error';
                        return { ok: false, reason: reason };
                    }
                }

                await sleep(500 * attempt);
            }

            return { ok: false, reason: 'Upload failed after retries' };
        }

        function sleep(ms) {
            return new Promise((resolve) => setTimeout(resolve, ms));
        }

        function updateBudgetIndicators() {
            const remainingSlots = Math.max(0, caseMaxFiles - uploadedFileCount);
            const remainingBytes = Math.max(0, totalSizeLimitBytes - currentTotalSizeBytes);
            fileCountElement.textContent = uploadedFileCount.toString();
            budgetFilesElement.textContent = uploadedFileCount + ' / ' + caseMaxFiles;
            budgetFilesRemainingElement.textContent = 'Remaining: ' + remainingSlots;
            budgetSizeElement.textContent = formatBytes(currentTotalSizeBytes) + ' / ' + formatBytes(totalSizeLimitBytes);
            budgetSizeRemainingElement.textContent = 'Remaining: ' + formatBytes(remainingBytes);
        }

        function formatBytes(bytes) {
            return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
        }

        function showClientAlert(tone, title, message) {
            clientAlertTitle.textContent = title;
            clientErrorMessage.textContent = message;
            clientErrorAlert.className = 'mb-6 p-4 rounded-lg border';
            if (tone === 'warning') {
                clientErrorAlert.classList.add('bg-amber-50', 'text-amber-900', 'border-amber-200');
            } else {
                clientErrorAlert.classList.add('bg-red-50', 'text-red-800', 'border-red-200');
            }
            clientErrorAlert.classList.remove('hidden');
        }

        function hideClientError() {
            clientAlertTitle.textContent = 'Selection needs attention';
            clientErrorMessage.textContent = '';
            clientErrorAlert.className = 'mb-6 p-4 rounded-lg border hidden';
            clientErrorAlert.classList.add('hidden');
        }

        function escapeHtml(text) {
            return text
                .replaceAll('&', '&amp;')
                .replaceAll('<', '&lt;')
                .replaceAll('>', '&gt;')
                .replaceAll('"', '&quot;')
                .replaceAll("'", '&#39;');
        }
    </script>
`)

