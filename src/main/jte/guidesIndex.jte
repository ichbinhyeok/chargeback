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
            Public checklists for Stripe and Shopify evidence submission. These guides focus on format and packaging constraints that trigger avoidable failures.
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
                <div class="text-xs uppercase tracking-wide text-primary-600 font-semibold mb-2">${guide.platformLabel()} - ${guide.reasonCodeLabel()}</div>
                <h2 class="text-xl font-semibold text-gray-900 mb-2">${guide.title()}</h2>
                <p class="text-sm text-gray-600 mb-4">${guide.metaDescription()}</p>
                <a href="${guide.path()}" class="cb-btn cb-btn-secondary">Read Guide</a>
            </article>
        @endfor
    </section>
`)
