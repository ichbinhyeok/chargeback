@import com.example.demo.dispute.persistence.DisputeCase
@import com.example.demo.dispute.api.CaseReportResponse
@import com.example.demo.dispute.domain.EvidenceType
@import com.example.demo.dispute.persistence.PaymentEntity
@import com.example.demo.dispute.service.ReasonCodeChecklistService
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
@param ReasonCodeChecklistService.ReasonChecklist reasonChecklist

@template.layout(
    title = "Upload | " + report.caseToken(),
    robotsMeta = "noindex,nofollow,noarchive",
    caseTokenForVault = report.caseToken(),
    casePlatformForVault = report.platform().name(),
    caseStateForVault = report.state().name(),
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
    <div id="client-error-alert" class="mb-6 p-4 rounded-lg bg-red-50 text-red-800 border border-red-200 hidden">
        <div class="font-semibold mb-1">Upload failed</div>
        <div id="client-error-message" class="text-sm whitespace-pre-line"></div>
    </div>

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
                <span>Upload and classify evidence files</span>
            </div>
        </div>
    </div>

    <div class="border-b border-gray-200 mb-8 overflow-x-auto shrink-0">
        <nav class="-mb-px flex space-x-8" aria-label="Tabs">
            <a href="/c/${report.caseToken()}" class="border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors">Dashboard</a>
            <a href="/c/${report.caseToken()}/upload" class="border-primary-600 text-primary-600 whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm transition-colors" aria-current="page">Upload & Validate</a>
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
            <p class="text-gray-500 text-sm mb-6 max-w-xs">After selecting files, choose evidence type per file in the next step.</p>

            <input type="file" id="file-input" multiple class="hidden" accept=".pdf,.png,.jpg,.jpeg">
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
                        <p class="text-slate-300 text-sm max-w-sm">Validation runs after upload. Auto-fix currently supports per-type merge and Shopify oversized image compression.</p>
                    </div>
                    <div class="flex flex-col gap-2 shrink-0">
                        <form method="post" action="/c/${report.caseToken()}/validate">
                            <button type="submit" class="w-full cb-btn bg-slate-800 border-slate-700 text-white hover:bg-slate-700 hover:border-slate-600 transition-colors shadow-sm">
                                Run Manual Check
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

    <script>
        const dropzone = document.getElementById('dropzone');
        const fileInput = document.getElementById('file-input');
        const progressBar = document.getElementById('upload-progress-bar');
        const progressContainer = document.getElementById('upload-progress-container');
        const statusText = document.getElementById('upload-status-text');
        const mappingModal = document.getElementById('mapping-modal');
        const mappingList = document.getElementById('mapping-list');
        const mappingCount = document.getElementById('mapping-count');
        const mappingStartUploadBtn = document.getElementById('mapping-start-upload-btn');
        const mappingCancelBtn = document.getElementById('mapping-cancel-btn');
        const mappingCloseBtn = document.getElementById('mapping-close-btn');
        const evidenceTypeSelectTemplate = document.getElementById('evidence-type-select-template');
        const clientErrorAlert = document.getElementById('client-error-alert');
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

        let pendingFiles = [];

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
            handleFiles(e.dataTransfer.files);
        });

        fileInput.addEventListener('change', function() {
            handleFiles(this.files);
            this.value = '';
        });

        mappingCloseBtn.addEventListener('click', closeMappingModal);
        mappingCancelBtn.addEventListener('click', closeMappingModal);
        mappingModal.addEventListener('click', (e) => {
            if (e.target === mappingModal) {
                closeMappingModal();
            }
        });
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && !mappingModal.classList.contains('hidden')) {
                closeMappingModal();
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
                closeMappingModal();
                await uploadMappedFiles(mappings);
            } finally {
                mappingStartUploadBtn.disabled = false;
                mappingStartUploadBtn.textContent = defaultUploadBtnText;
            }
        });

        function handleFiles(fileList) {
            if (!fileList || fileList.length === 0) {
                return;
            }
            hideClientError();
            const errors = [];
            const selectedFiles = Array.from(fileList);
            const accepted = [];
            const rejected = [];
            for (const file of selectedFiles) {
                const lower = file.name.toLowerCase();
                const supported = lower.endsWith('.pdf') || lower.endsWith('.png') || lower.endsWith('.jpg') || lower.endsWith('.jpeg');
                if (supported) {
                    accepted.push(file);
                } else {
                    rejected.push(file.name);
                }
            }
            if (rejected.length > 0) {
                errors.push('Unsupported file type (allowed: PDF, PNG, JPG, JPEG):\n- ' + rejected.join('\n- '));
            }
            if (accepted.length === 0) {
                if (errors.length > 0) {
                    showClientError(errors.join('\n\n'));
                }
                return;
            }

            const remainingSlots = caseMaxFiles - uploadedFileCount;
            if (remainingSlots <= 0) {
                errors.push('Case file limit reached (' + caseMaxFiles + '). Delete or merge files before adding more.');
                showClientError(errors.join('\n\n'));
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
                showClientError(errors.join('\n\n'));
            }

            pendingFiles = cappedAccepted;
            if (pendingFiles.length === 0) {
                return;
            }
            renderMappingRows();
            openMappingModal();
        }

        function openMappingModal() {
            mappingModal.classList.remove('hidden');
            mappingModal.classList.add('flex');
        }

        function closeMappingModal() {
            mappingModal.classList.remove('flex');
            mappingModal.classList.add('hidden');
        }

        function renderMappingRows() {
            mappingList.innerHTML = '';
            mappingCount.textContent = pendingFiles.length.toString();
            pendingFiles.forEach((file, index) => {
                const row = document.createElement('div');
                row.className = 'border border-gray-200 rounded-lg p-3 flex flex-col md:flex-row md:items-center gap-3 justify-between';
                row.setAttribute('data-row-index', index.toString());

                const info = document.createElement('div');
                info.className = 'min-w-0';
                const kb = (file.size / 1024).toFixed(1);
                info.innerHTML =
                    '<div class="font-medium text-gray-900 truncate">' + escapeHtml(file.name) + '</div>' +
                    '<div class="text-xs text-gray-500 mt-1">' + kb + ' KB</div>';

                const right = document.createElement('div');
                right.className = 'flex items-center gap-2';

                const select = evidenceTypeSelectTemplate.content.firstElementChild.cloneNode(true);
                select.value = guessEvidenceType(file.name);
                right.appendChild(select);

                const removeBtn = document.createElement('button');
                removeBtn.type = 'button';
                removeBtn.className = 'cb-btn cb-btn-secondary text-xs px-3 py-2';
                removeBtn.textContent = 'Remove';
                removeBtn.addEventListener('click', () => {
                    pendingFiles = pendingFiles.filter((_, i) => i !== index);
                    renderMappingRows();
                });
                right.appendChild(removeBtn);

                row.appendChild(info);
                row.appendChild(right);
                mappingList.appendChild(row);
            });

            mappingStartUploadBtn.disabled = pendingFiles.length === 0;
        }

        function guessEvidenceType(fileName) {
            const lower = fileName.toLowerCase();
            if (lower.includes('receipt') || lower.includes('invoice') || lower.includes('order')) return 'ORDER_RECEIPT';
            if (lower.includes('policy') || lower.includes('terms')) return 'POLICIES';
            if (lower.includes('tracking') || lower.includes('delivery') || lower.includes('shipment') || lower.includes('fulfillment')) return 'FULFILLMENT_DELIVERY';
            if (lower.includes('message') || lower.includes('chat') || lower.includes('email') || lower.includes('communication')) return 'CUSTOMER_COMMUNICATION';
            if (lower.includes('refund') || lower.includes('cancel')) return 'REFUND_CANCELLATION';
            if (lower.includes('customer') || lower.includes('address') || lower.includes('profile')) return 'CUSTOMER_DETAILS';
            if (lower.includes('usage') || lower.includes('log') || lower.includes('ip') || lower.includes('digital')) return 'DIGITAL_USAGE_LOGS';
            return 'OTHER_SUPPORTING';
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
                showClientError('Some files failed to upload:\n- ' + failures.join('\n- '));
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
                    showClientError('Files uploaded, but validation failed. Please run manual check.');
                    statusText.innerText = 'Validation request failed.';
                    return;
                }
            } catch (error) {
                showClientError('Files uploaded, but validation request failed. Please run manual check.');
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

        function showClientError(message) {
            clientErrorMessage.textContent = message;
            clientErrorAlert.classList.remove('hidden');
        }

        function hideClientError() {
            clientErrorMessage.textContent = '';
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
