@import java.util.List
@import com.example.demo.dispute.web.GuidePageView
@param GuidePageView guide
@param List<GuidePageView> relatedGuides
@param List<GuidePageView> sameTypeRelatedGuides
@param List<GuidePageView> crossTypeRelatedGuides
@param List<GuidePageView> keywordRelatedGuides
@param String canonicalUrl
@param String metaDescription
@param String heroTitle
@param String heroDescription
@param String failureBefore
@param String failureAfter
@param String failureResult

@template.layout(
    title = heroTitle + " | Chargeback Builder",
    canonicalUrl = canonicalUrl,
    metaDescription = metaDescription,
    content = @`
    <article class="max-w-5xl" data-seo-page="guide-detail" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">
        <div class="text-sm mb-4">
            <a href="/guides" class="text-primary-600 hover:underline">Help Center</a>
            <span class="text-gray-400"> / </span>
            <a href="${guide.platformPath()}" class="text-primary-600 hover:underline">${guide.platformLabel()}</a>
            <span class="text-gray-400"> / </span>
            <span class="text-gray-600">${guide.reasonCodeLabel()}</span>
        </div>

        <section class="mb-8 overflow-hidden rounded-2xl border border-blue-200 bg-gradient-to-br from-white via-primary-50 to-blue-50 p-6 md:p-8 shadow-sm">
            <div class="mb-3 flex flex-wrap gap-2">
                <span class="cb-badge cb-badge-blue">${guide.platformLabel()}</span>
                <span class="cb-badge cb-badge-gray">${guide.guideTypeLabel()}</span>
            </div>
            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
                <div class="lg:col-span-2">
                    <p class="text-xs font-semibold uppercase tracking-widest text-primary-700 mb-3">Stop Trial-And-Error Uploads</p>
                    <h1 class="text-3xl md:text-4xl font-bold text-gray-900 mb-3">${heroTitle}</h1>
                    <p class="text-lg text-gray-700 mb-4 max-w-3xl">${heroDescription}</p>
                    <p class="text-sm text-gray-700 max-w-3xl">
                        This page gives you the manual checklist. Chargeback Builder turns it into a workflow: validate the exact files for this issue, auto-fix supported blockers, and export the final submission pack when it is ready.
                    </p>
                    <div class="mt-6 flex flex-col sm:flex-row gap-3">
                        <a href="/new?src=guide&platform=${guide.platformSlug()}&guide=${guide.reasonCodeSlug()}" class="cb-btn cb-btn-primary" data-seo-event="guide_start_case_click" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">Start Free Validation</a>
                        <a href="#required-checklist" class="cb-btn cb-btn-secondary">See Manual Checklist</a>
                    </div>
                    <div class="mt-6 flex flex-wrap gap-3 text-sm">
                        <span class="inline-flex items-center rounded-full border border-blue-100 bg-white px-3 py-1 font-medium text-blue-800">Free validation first</span>
                        <span class="inline-flex items-center rounded-full border border-green-100 bg-white px-3 py-1 font-medium text-green-800">Auto-fix supported blockers</span>
                        <span class="inline-flex items-center rounded-full border border-gray-200 bg-white px-3 py-1 font-medium text-gray-700">ZIP + PDF + TXT when ready</span>
                    </div>
                </div>
                <div class="rounded-2xl border border-white/80 bg-white/90 p-5 shadow-sm">
                    <p class="text-xs font-semibold uppercase tracking-wide text-primary-600">Quick answer</p>
                    @if(guide.isErrorGuide())
                        <p class="mt-3 text-sm text-gray-700">This is usually a packaging or upload blocker, not a reason to keep retrying the same file set. Fix the evidence package first, then submit.</p>
                    @else
                        <p class="mt-3 text-sm text-gray-700">This is usually an evidence coverage problem. Build the required file set first, then use the explanation draft to tighten the final submission.</p>
                    @endif
                    <dl class="mt-4 space-y-3 text-sm">
                        <div>
                            <dt class="text-gray-500">Platform</dt>
                            <dd class="font-medium text-gray-900">${guide.platformLabel()}</dd>
                        </div>
                        <div>
                            <dt class="text-gray-500">Focus</dt>
                            <dd class="font-medium text-gray-900">${guide.reasonCodeLabel()}</dd>
                        </div>
                        <div>
                            <dt class="text-gray-500">Best next step</dt>
                            <dd class="font-medium text-gray-900">Start a free validation case from this page and upload only the files tied to this blocker.</dd>
                        </div>
                    </dl>
                </div>
            </div>
        </section>

        <section class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            <div class="cb-card">
                <h2 class="text-base font-semibold text-gray-900 mb-2">Know what is actually blocking upload</h2>
                <p class="text-sm text-gray-600">Use this page to confirm whether the problem is file size, format, duplicate evidence, or missing proof for the reason code.</p>
            </div>
            <div class="cb-card">
                <h2 class="text-base font-semibold text-gray-900 mb-2">Fix the blocker before you re-upload</h2>
                <p class="text-sm text-gray-600">Validation shows what to remove, compress, merge, or replace so you stop wasting attempts with the same rejected package.</p>
            </div>
            <div class="cb-card">
                <h2 class="text-base font-semibold text-gray-900 mb-2">Finish with a submission-ready pack</h2>
                <p class="text-sm text-gray-600">When blockers are clear, export the ZIP, summary PDF, and explanation draft for the actual dispute submission flow.</p>
            </div>
        </section>

        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div class="lg:col-span-2 space-y-6">
                <section class="cb-card">
                    <h2 class="text-lg font-semibold text-gray-900 mb-2">1. Confirm this is your blocker</h2>
                    <p class="text-sm text-gray-600 mb-3">If these symptoms match, use this checklist before editing files or retrying the upload.</p>
                    <ul class="list-disc pl-5 space-y-2 text-sm text-gray-700">
                        @for(String err : guide.commonErrors())
                            <li>${err}</li>
                        @endfor
                    </ul>
                </section>

                @if(failureBefore != null && !failureBefore.isBlank() && failureAfter != null && !failureAfter.isBlank())
                    <section class="cb-card">
                        <h2 class="text-lg font-semibold text-gray-900 mb-3">Before / After Fix Snapshot</h2>
                        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                            <div class="rounded-lg border border-red-200 bg-red-50 p-4">
                                <p class="text-xs font-semibold uppercase tracking-wide text-red-700 mb-2">Before</p>
                                <p class="text-red-900">${failureBefore}</p>
                            </div>
                            <div class="rounded-lg border border-green-200 bg-green-50 p-4">
                                <p class="text-xs font-semibold uppercase tracking-wide text-green-700 mb-2">After</p>
                                <p class="text-green-900">${failureAfter}</p>
                            </div>
                        </div>
                        @if(failureResult != null && !failureResult.isBlank())
                            <p class="mt-3 text-sm text-gray-700"><strong>Expected result:</strong> ${failureResult}</p>
                        @endif
                    </section>
                @endif

                <section class="cb-card border-2 border-primary-600">
                    <h2 class="text-lg font-semibold text-gray-900 mb-2">2. Fastest path inside Chargeback Builder</h2>
                    <p class="text-sm text-gray-600 mb-3">Use the page as the checklist. Use the app as the workflow.</p>
                    <ol class="list-decimal pl-5 space-y-2 text-sm text-gray-700">
                        <li>Start a case from this page so the platform and issue context are already attached.</li>
                        <li>Upload only files tied to this blocker and remove duplicate evidence-type files before validating.</li>
                        <li>Run validation, apply auto-fix where supported, and stop only when no blocking issues remain.</li>
                        <li>Export the final submission package and upload it to your Stripe or Shopify dispute dashboard.</li>
                    </ol>
                    <div class="mt-4 flex flex-col sm:flex-row gap-3">
                        <a href="/new?src=guide&platform=${guide.platformSlug()}&guide=${guide.reasonCodeSlug()}" class="cb-btn cb-btn-primary" data-seo-event="guide_start_case_click" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">Start Free Validation</a>
                        <a href="#required-checklist" class="cb-btn cb-btn-secondary">Use The Checklist Manually</a>
                    </div>
                </section>

                <section id="required-checklist" class="cb-card">
                    <h2 class="text-lg font-semibold text-gray-900 mb-2">3. Manual evidence checklist</h2>
                    <p class="text-sm text-gray-600 mb-3">Use this even if you fix the issue outside the app. These are the items that should be true before final upload.</p>
                    <ul class="list-disc pl-5 space-y-2 text-sm text-gray-700">
                        @for(String check : guide.keyChecks())
                            <li>${check}</li>
                        @endfor
                    </ul>
                </section>

                <section class="cb-card">
                    <h2 class="text-lg font-semibold text-gray-900 mb-2">4. Manual fix steps</h2>
                    <p class="text-sm text-gray-600 mb-3">If you stay manual, work through these steps in order before you upload again.</p>
                    <ol class="list-decimal pl-5 space-y-2 text-sm text-gray-700">
                        @for(String step : guide.nextSteps())
                            <li>${step}</li>
                        @endfor
                    </ol>
                </section>

                @if(!guide.explanationPreviewLines().isEmpty())
                    <section class="cb-card">
                        <h2 class="text-lg font-semibold text-gray-900 mb-3">5. Explanation draft starter</h2>
                        <div class="rounded-lg border border-gray-200 bg-gray-50 p-4 text-sm text-gray-700 space-y-2">
                            @for(String line : guide.explanationPreviewLines())
                                <p>${line}</p>
                            @endfor
                        </div>
                        <p class="mt-3 text-sm text-gray-600">Use this as a starting point only. Add transaction-specific facts before submission.</p>
                    </section>
                @endif

                @if(!guide.sourceUrls().isEmpty())
                    <section id="official-sources" class="cb-card">
                        <h2 class="text-lg font-semibold text-gray-900 mb-2">Official platform sources</h2>
                        <p class="text-sm text-gray-600 mb-3">Double-check current platform rules here. Use Chargeback Builder to handle validation and packaging around those rules.</p>
                        <ul class="list-disc pl-5 space-y-2 text-sm text-gray-700">
                            @for(String sourceUrl : guide.sourceUrls())
                                <li><a href="${sourceUrl}" target="_blank" rel="nofollow noopener noreferrer" class="text-primary-600 hover:underline">${sourceUrl}</a></li>
                            @endfor
                        </ul>
                    </section>
                @endif
            </div>

            <aside class="space-y-6">
                <section class="cb-card lg:sticky lg:top-24">
                    <h2 class="text-lg font-semibold text-gray-900 mb-3">What you get when you start here</h2>
                    <ul class="list-disc pl-5 space-y-2 text-sm text-gray-700">
                        <li>A case opened with this platform and issue context already attached.</li>
                        <li>Validation that catches format, size, duplicate-file, and evidence-gap blockers before submission.</li>
                        <li>Auto-fix for supported blockers, instead of manually rebuilding every file.</li>
                        <li>An export-ready ZIP, summary PDF, and explanation draft once the pack is clean.</li>
                    </ul>
                    <a href="/new?src=guide&platform=${guide.platformSlug()}&guide=${guide.reasonCodeSlug()}" class="mt-4 cb-btn cb-btn-primary w-full" data-seo-event="guide_start_case_click" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">Start Free Validation</a>
                    <p class="mt-3 text-xs text-gray-500">No login required. Resume later with the case link or access key file.</p>
                </section>

                <section class="cb-card">
                    <h2 class="text-lg font-semibold text-gray-900 mb-3">If you stay manual, do not skip this</h2>
                    <ul class="list-disc pl-5 space-y-2 text-sm text-gray-700">
                        <li>Do not retry the same rejected file package without changing it.</li>
                        <li>Keep only evidence tied to this specific blocker or reason code.</li>
                        <li>Re-check the current platform rules before the final upload.</li>
                    </ul>
                </section>

                <section class="rounded-lg border border-blue-100 bg-blue-50 p-4 text-sm text-blue-900">
                    <p class="font-semibold mb-2">Privacy & Trust</p>
                    <ul class="list-disc pl-5 space-y-1">
                        <li>Case routes are protected with <code>noindex / no-store / no-referrer</code> headers.</li>
                        <li>Default file retention is 30 days, with due-date-aware extension when needed.</li>
                        <li>Immediate manual deletion is available from the case dashboard.</li>
                    </ul>
                </section>
            </aside>
        </div>

        @if(sameTypeRelatedGuides != null && !sameTypeRelatedGuides.isEmpty())
            <section class="cb-card mt-6 mb-6">
                <h2 class="text-lg font-semibold text-gray-900 mb-3">More ${guide.guideTypeLabel()} Topics</h2>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                    @for(GuidePageView related : sameTypeRelatedGuides)
                        <a href="${related.path()}" class="block rounded-lg border border-gray-200 p-3 hover:border-primary-300 hover:bg-primary-50/20 transition-colors">
                            <p class="text-xs uppercase tracking-wide text-primary-600 font-semibold mb-1">${related.guideTypeLabel()}</p>
                            <p class="text-sm font-semibold text-gray-900 mb-1">${related.reasonCodeLabel()}</p>
                            <p class="text-xs text-gray-600">${related.metaDescription()}</p>
                        </a>
                    @endfor
                </div>
            </section>
        @endif

        @if(crossTypeRelatedGuides != null && !crossTypeRelatedGuides.isEmpty())
            <section class="cb-card mb-6">
                <h2 class="text-lg font-semibold text-gray-900 mb-3">Next Step ${guide.isErrorGuide() ? "Reason-Code Checklists" : "Upload/Error Fix Topics"}</h2>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                    @for(GuidePageView related : crossTypeRelatedGuides)
                        <a href="${related.path()}" class="block rounded-lg border border-gray-200 p-3 hover:border-primary-300 hover:bg-primary-50/20 transition-colors">
                            <p class="text-xs uppercase tracking-wide text-primary-600 font-semibold mb-1">${related.guideTypeLabel()}</p>
                            <p class="text-sm font-semibold text-gray-900 mb-1">${related.reasonCodeLabel()}</p>
                            <p class="text-xs text-gray-600">${related.metaDescription()}</p>
                        </a>
                    @endfor
                </div>
            </section>
        @endif

        @if(keywordRelatedGuides != null && !keywordRelatedGuides.isEmpty())
            <section class="cb-card mb-6">
                <h2 class="text-lg font-semibold text-gray-900 mb-3">Related ${guide.platformLabel()} Topics</h2>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                    @for(GuidePageView related : keywordRelatedGuides)
                        <a href="${related.path()}" class="block rounded-lg border border-gray-200 p-3 hover:border-primary-300 hover:bg-primary-50/20 transition-colors">
                            <p class="text-xs uppercase tracking-wide text-primary-600 font-semibold mb-1">${related.guideTypeLabel()}</p>
                            <p class="text-sm font-semibold text-gray-900 mb-1">${related.reasonCodeLabel()}</p>
                            <p class="text-xs text-gray-600">${related.metaDescription()}</p>
                        </a>
                    @endfor
                </div>
            </section>
        @endif

        @if(relatedGuides != null && !relatedGuides.isEmpty())
            <section class="mb-6 text-sm">
                <p class="text-gray-600 mb-2">Continue browsing:</p>
                <div class="flex flex-wrap gap-2">
                    <a href="/guides" class="cb-btn cb-btn-secondary">All Help Topics</a>
                    <a href="${guide.platformPath()}" class="cb-btn cb-btn-secondary">${guide.platformLabel()} Overview</a>
                    <a href="/new?src=guide&platform=${guide.platformSlug()}&guide=${guide.reasonCodeSlug()}" class="cb-btn cb-btn-primary" data-seo-event="guide_start_case_click" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">Start Free Validation</a>
                </div>
            </section>
        @endif

        <section class="cb-card mb-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-3">FAQ</h2>
            <div class="space-y-3 text-sm text-gray-700">
                @for(int i = 0; i < guide.faqItems().size(); i++)
                    <div>
                        <p class="font-semibold text-gray-900">${guide.faqItems().get(i).question()}</p>
                        <p class="mt-1">${guide.faqItems().get(i).answer()}</p>
                    </div>
                @endfor
            </div>
        </section>

        <section class="text-xs text-gray-500">
            <p>This guide focuses on evidence formatting and submission quality. It is not legal advice and does not guarantee dispute outcomes.</p>
        </section>

        <script type="application/ld+json">
        {
          "@context": "https://schema.org",
          "@type": "FAQPage",
          "mainEntity": [
            @for(int i = 0; i < guide.faqItems().size(); i++)
            {
              "@type": "Question",
              "name": "${guide.faqItems().get(i).question()}",
              "acceptedAnswer": {
                "@type": "Answer",
                "text": "${guide.faqItems().get(i).answer()}"
              }
            }@if(i < guide.faqItems().size() - 1),@endif
            @endfor
          ]
        }
        </script>
    </article>
`)
