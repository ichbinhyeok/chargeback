@import java.util.List
@import com.example.demo.dispute.web.GuidePageView
@param GuidePageView guide
@param List<GuidePageView> relatedGuides
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
    <article class="max-w-4xl" data-seo-page="guide-detail" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">
        <div class="text-sm mb-4">
            <a href="/guides" class="text-primary-600 hover:underline">Guides</a>
            <span class="text-gray-400"> / </span>
            <a href="${guide.platformPath()}" class="text-primary-600 hover:underline">${guide.platformLabel()}</a>
            <span class="text-gray-400"> / </span>
            <span class="text-gray-600">${guide.reasonCodeLabel()}</span>
        </div>

        <div class="mb-3 flex flex-wrap gap-2">
            <span class="cb-badge cb-badge-blue">${guide.platformLabel()}</span>
            <span class="cb-badge cb-badge-gray">${guide.guideTypeLabel()}</span>
        </div>
        <h1 class="text-3xl font-bold text-gray-900 mb-3">${heroTitle}</h1>
        <p class="text-gray-600 mb-8">${heroDescription}</p>

        @if(!guide.targetSearchQueries().isEmpty())
            <section class="cb-card mb-6">
                <h2 class="text-lg font-semibold text-gray-900 mb-3">Search Phrases This Page Solves</h2>
                <ul class="list-disc pl-5 space-y-2 text-sm text-gray-700">
                    @for(String keyword : guide.targetSearchQueries())
                        <li>${keyword}</li>
                    @endfor
                </ul>
            </section>
        @endif

        <section class="cb-card mb-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-3">1. Why Upload Fails</h2>
            <ul class="list-disc pl-5 space-y-2 text-sm text-gray-700">
                @for(String err : guide.commonErrors())
                    <li>${err}</li>
                @endfor
            </ul>
        </section>

        <section class="cb-card mb-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-3">2. Required Evidence Checklist</h2>
            <ul class="list-disc pl-5 space-y-2 text-sm text-gray-700">
                @for(String check : guide.keyChecks())
                    <li>${check}</li>
                @endfor
            </ul>
        </section>

        <section class="cb-card mb-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-3">3. Fix Steps</h2>
            <ol class="list-decimal pl-5 space-y-2 text-sm text-gray-700">
                @for(String step : guide.nextSteps())
                    <li>${step}</li>
                @endfor
            </ol>
        </section>

        <section class="cb-card mb-6 border-2 border-primary-600">
            <h2 class="text-lg font-semibold text-gray-900 mb-3">4. 3-Minute Recovery Flow</h2>
            <ol class="list-decimal pl-5 space-y-2 text-sm text-gray-700">
                <li>Upload only files tied to this specific blocker and remove duplicate evidence-type files.</li>
                <li>Run validation, apply auto-fix where available, then stop only when no blocking errors remain.</li>
                <li>Export the submission package and upload it to your Stripe/Shopify dispute dashboard.</li>
            </ol>
            <div class="mt-4 flex flex-wrap gap-3">
                <a href="/new?src=guide&platform=${guide.platformSlug()}&guide=${guide.reasonCodeSlug()}" class="cb-btn cb-btn-primary" data-seo-event="guide_start_case_click" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">Start 3-Minute Fix</a>
            </div>
        </section>

        @if(failureBefore != null && !failureBefore.isBlank() && failureAfter != null && !failureAfter.isBlank())
            <section class="cb-card mb-6">
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

        @if(!guide.explanationPreviewLines().isEmpty())
            <section class="cb-card mb-6">
                <h2 class="text-lg font-semibold text-gray-900 mb-3">Explanation Draft Sample</h2>
                <div class="rounded-lg border border-gray-200 bg-gray-50 p-4 text-sm text-gray-700 space-y-2">
                    @for(String line : guide.explanationPreviewLines())
                        <p>${line}</p>
                    @endfor
                </div>
                <p class="mt-3 text-sm text-gray-600">Use this sample as writing support only. Always adapt with transaction-specific facts before submission.</p>
            </section>
        @endif

        @if(!guide.sourceUrls().isEmpty())
            <section class="cb-card mb-6">
                <h2 class="text-lg font-semibold text-gray-900 mb-3">Policy Sources</h2>
                <ul class="list-disc pl-5 space-y-2 text-sm text-gray-700">
                    @for(String sourceUrl : guide.sourceUrls())
                        <li><a href="${sourceUrl}" target="_blank" rel="nofollow noopener noreferrer" class="text-primary-600 hover:underline">${sourceUrl}</a></li>
                    @endfor
                </ul>
            </section>
        @endif

        @if(relatedGuides != null && !relatedGuides.isEmpty())
            <section class="cb-card mb-6">
                <h2 class="text-lg font-semibold text-gray-900 mb-3">Related ${guide.platformLabel()} Guides</h2>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                    @for(GuidePageView related : relatedGuides)
                        <a href="${related.path()}" class="block rounded-lg border border-gray-200 p-3 hover:border-primary-300 hover:bg-primary-50/20 transition-colors">
                            <p class="text-xs uppercase tracking-wide text-primary-600 font-semibold mb-1">${related.guideTypeLabel()}</p>
                            <p class="text-sm font-semibold text-gray-900 mb-1">${related.reasonCodeLabel()}</p>
                            <p class="text-xs text-gray-600">${related.metaDescription()}</p>
                        </a>
                    @endfor
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

        <section class="mt-4 rounded-lg border border-blue-100 bg-blue-50 p-4 text-sm text-blue-900">
            <p class="font-semibold mb-2">Privacy & Trust</p>
            <ul class="list-disc pl-5 space-y-1">
                <li>Case routes are protected with <code>noindex / no-store / no-referrer</code> headers.</li>
                <li>Default file retention is 30 days, with due-date-aware extension when needed.</li>
                <li>Immediate manual deletion is available from the case dashboard.</li>
            </ul>
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
