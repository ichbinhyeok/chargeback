@import java.util.List
@import java.util.Set
@import com.example.demo.dispute.web.GuidePageView
@param List<GuidePageView> guides
@param Set<String> platforms
@param String canonicalUrl
@param String metaDescription

@template.layout(
    title = "Dispute Evidence Guides | Chargeback Builder",
    canonicalUrl = canonicalUrl,
    metaDescription = metaDescription,
    content = @`
    <section class="mb-10">
        <h1 class="text-3xl font-bold text-gray-900 mb-3">Dispute Evidence Guides</h1>
        <p class="text-gray-600 max-w-2xl">
            Public Stripe and Shopify guides focused on high-intent upload failures (file size, format, link errors) plus reason-code evidence checklists.
        </p>
    </section>

    <section class="cb-card mb-8">
        <h2 class="text-lg font-semibold text-gray-900 mb-3">Browse By Platform</h2>
        <div class="flex flex-wrap gap-3">
            @for(String platform : platforms)
                <a href="/guides/${platform}" class="cb-btn cb-btn-secondary">${platform.toUpperCase()}</a>
            @endfor
        </div>
    </section>

    <section class="grid grid-cols-1 md:grid-cols-2 gap-5">
        @for(GuidePageView guide : guides)
            <article class="cb-card">
                <div class="mb-2 flex items-center gap-2">
                    <span class="cb-badge cb-badge-blue">${guide.platformLabel()}</span>
                    <span class="cb-badge cb-badge-gray">${guide.guideTypeLabel()}</span>
                </div>
                <h2 class="text-xl font-semibold text-gray-900 mb-2">${guide.title()}</h2>
                <p class="text-sm text-gray-600 mb-4">${guide.metaDescription()}</p>
                <p class="text-xs text-gray-500 mb-4">${guide.reasonCodeLabel()}</p>
                <a href="${guide.path()}" class="cb-btn cb-btn-secondary">Read Guide</a>
            </article>
        @endfor
    </section>
`)
