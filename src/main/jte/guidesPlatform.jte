@import java.util.List
@import com.example.demo.dispute.web.GuidePageView
@param String platform
@param String platformLabel
@param List<GuidePageView> guides
@param List<GuidePageView> errorGuides
@param List<GuidePageView> reasonGuides
@param String canonicalUrl
@param String metaDescription

@template.layout(
    title = platformLabel + " Upload Error Fixes | Chargeback Builder",
    canonicalUrl = canonicalUrl,
    metaDescription = metaDescription,
    content = @`
    <section class="mb-8">
        <a href="/guides" class="text-sm text-primary-600 hover:underline">All Help Topics</a>
        <h1 class="text-3xl font-bold text-gray-900 mt-2 mb-2">${platformLabel} Error Fixes &amp; Evidence Checklists</h1>
        <p class="text-gray-600 max-w-3xl">Use the checklist to confirm the issue, then start free validation to catch blockers before you upload anything back to ${platformLabel}.</p>
        <div class="mt-5 flex flex-wrap gap-3 text-sm">
            <span class="inline-flex items-center rounded-full border border-blue-100 bg-blue-50 px-3 py-1 font-medium text-blue-800">Start from the exact blocker</span>
            <span class="inline-flex items-center rounded-full border border-green-100 bg-green-50 px-3 py-1 font-medium text-green-800">Validate before re-uploading</span>
            <span class="inline-flex items-center rounded-full border border-gray-200 bg-white px-3 py-1 font-medium text-gray-700">Export the final pack when ready</span>
        </div>
    </section>

    @if(errorGuides != null && !errorGuides.isEmpty())
        <section class="mb-8">
            <h2 class="text-xl font-semibold text-gray-900 mb-3">Upload/Error Fixes</h2>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
                @for(GuidePageView guide : errorGuides)
                    <article class="cb-card">
                        <div class="text-xs uppercase tracking-wide text-primary-600 font-semibold mb-2">${guide.reasonCodeLabel()}</div>
                        <h3 class="text-lg font-semibold text-gray-900 mb-2">${guide.title()}</h3>
                        <p class="text-sm text-gray-600 mb-4">${guide.metaDescription()}</p>
                        <a href="/new?src=guide&platform=${guide.platformSlug()}&guide=${guide.reasonCodeSlug()}" class="cb-btn cb-btn-primary" data-seo-event="guide_start_case_click" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">Start Free Validation</a>
                        <a href="${guide.path()}" class="mt-3 inline-block text-sm text-primary-600 hover:underline">See checklist &amp; steps</a>
                    </article>
                @endfor
            </div>
        </section>
    @endif

    @if(reasonGuides != null && !reasonGuides.isEmpty())
        <section>
            <h2 class="text-xl font-semibold text-gray-900 mb-3">Reason-Code Checklists</h2>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
                @for(GuidePageView guide : reasonGuides)
                    <article class="cb-card">
                        <div class="text-xs uppercase tracking-wide text-primary-600 font-semibold mb-2">${guide.reasonCodeLabel()}</div>
                        <h3 class="text-lg font-semibold text-gray-900 mb-2">${guide.title()}</h3>
                        <p class="text-sm text-gray-600 mb-4">${guide.metaDescription()}</p>
                        <a href="/new?src=guide&platform=${guide.platformSlug()}&guide=${guide.reasonCodeSlug()}" class="cb-btn cb-btn-primary" data-seo-event="guide_start_case_click" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">Start Free Validation</a>
                        <a href="${guide.path()}" class="mt-3 inline-block text-sm text-primary-600 hover:underline">See checklist &amp; steps</a>
                    </article>
                @endfor
            </div>
        </section>
    @endif
`)
