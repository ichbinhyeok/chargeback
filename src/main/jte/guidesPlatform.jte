@import java.util.List
@import com.example.demo.dispute.web.GuidePageView
@param String platform
@param String platformLabel
@param List<GuidePageView> guides
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
        <p class="text-gray-600">Reason-code specific checklists for ${platformLabel} dispute evidence packaging.</p>
    </section>

    <section class="grid grid-cols-1 md:grid-cols-2 gap-5">
        @for(GuidePageView guide : guides)
            <article class="cb-card">
                <div class="text-xs uppercase tracking-wide text-primary-600 font-semibold mb-2">${guide.reasonCodeLabel()}</div>
                <h2 class="text-lg font-semibold text-gray-900 mb-2">${guide.title()}</h2>
                <p class="text-sm text-gray-600 mb-4">${guide.metaDescription()}</p>
                <a href="${guide.path()}" class="cb-btn cb-btn-secondary">Open Checklist</a>
            </article>
        @endfor
    </section>
`)
