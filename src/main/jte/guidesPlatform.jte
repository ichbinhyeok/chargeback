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
    title = platformLabel + " Evidence Guides | Chargeback Builder",
    canonicalUrl = canonicalUrl,
    metaDescription = metaDescription,
    content = @`
    <section class="mb-8">
        <a href="/guides" class="text-sm text-primary-600 hover:underline">All Guides</a>
        <h1 class="text-3xl font-bold text-gray-900 mt-2 mb-2">${platformLabel} Evidence Guides</h1>
        <p class="text-gray-600">Upload-error fixes and reason-code checklists for ${platformLabel} dispute evidence packaging.</p>
    </section>

    @if(errorGuides != null && !errorGuides.isEmpty())
        <section class="mb-8">
            <h2 class="text-xl font-semibold text-gray-900 mb-3">Upload/Error Fix Guides</h2>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
                @for(GuidePageView guide : errorGuides)
                    <article class="cb-card">
                        <div class="text-xs uppercase tracking-wide text-primary-600 font-semibold mb-2">${guide.reasonCodeLabel()}</div>
                        <h3 class="text-lg font-semibold text-gray-900 mb-2">${guide.title()}</h3>
                        <p class="text-sm text-gray-600 mb-4">${guide.metaDescription()}</p>
                        <a href="/new?src=guide&platform=${guide.platformSlug()}&guide=${guide.reasonCodeSlug()}" class="cb-btn cb-btn-primary" data-seo-event="guide_start_case_click" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">Start 3-Minute Fix</a>
                        <a href="${guide.path()}" class="mt-3 inline-block text-sm text-primary-600 hover:underline">View guide details</a>
                    </article>
                @endfor
            </div>
        </section>
    @endif

    @if(reasonGuides != null && !reasonGuides.isEmpty())
        <section>
            <h2 class="text-xl font-semibold text-gray-900 mb-3">Reason-Code Evidence Guides</h2>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
                @for(GuidePageView guide : reasonGuides)
                    <article class="cb-card">
                        <div class="text-xs uppercase tracking-wide text-primary-600 font-semibold mb-2">${guide.reasonCodeLabel()}</div>
                        <h3 class="text-lg font-semibold text-gray-900 mb-2">${guide.title()}</h3>
                        <p class="text-sm text-gray-600 mb-4">${guide.metaDescription()}</p>
                        <a href="/new?src=guide&platform=${guide.platformSlug()}&guide=${guide.reasonCodeSlug()}" class="cb-btn cb-btn-primary" data-seo-event="guide_start_case_click" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">Start 3-Minute Fix</a>
                        <a href="${guide.path()}" class="mt-3 inline-block text-sm text-primary-600 hover:underline">View guide details</a>
                    </article>
                @endfor
            </div>
        </section>
    @endif
`)
