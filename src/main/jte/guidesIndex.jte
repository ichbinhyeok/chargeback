@import java.util.List
@import java.util.Set
@import com.example.demo.dispute.web.GuidePageView
@param List<GuidePageView> guides
@param Set<String> platforms
@param String canonicalUrl
@param String metaDescription
@param String routerQuery
@param String routerPlatform
@param boolean routerPlatformStripe
@param boolean routerPlatformShopify
@param boolean routerNoMatch
@param String routerStartFixUrl

@template.layout(
    title = "Fix Upload Errors & Evidence Checklists | Chargeback Builder",
    canonicalUrl = canonicalUrl,
    metaDescription = metaDescription,
    content = @`
    <section class="mb-10">
        <h1 class="text-3xl font-bold text-gray-900 mb-3">Error Fixes &amp; Evidence Checklists</h1>
        <p class="text-gray-600 max-w-2xl">
            Get the manual checklist fast, then move into Chargeback Builder to validate files, auto-fix supported blockers, and finish the submission pack.
        </p>
        <div class="mt-5 flex flex-wrap gap-3 text-sm">
            <span class="inline-flex items-center rounded-full border border-blue-100 bg-blue-50 px-3 py-1 font-medium text-blue-800">Free validation first</span>
            <span class="inline-flex items-center rounded-full border border-green-100 bg-green-50 px-3 py-1 font-medium text-green-800">Auto-fix supported blockers</span>
            <span class="inline-flex items-center rounded-full border border-gray-200 bg-white px-3 py-1 font-medium text-gray-700">ZIP + PDF + TXT when ready</span>
        </div>
    </section>

    <section class="cb-card mb-8 border-2 border-primary-600">
        <h2 class="text-lg font-semibold text-gray-900 mb-3">Find My Error Fast</h2>
        <p class="text-sm text-gray-600 mb-4">Paste the exact upload error and jump straight into the best fix flow instead of reading platform docs first.</p>
        <form method="get" action="/guides/router" class="grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
            <div class="md:col-span-3">
                <label for="routerQ" class="block text-sm font-medium text-gray-700 mb-1">Error Text</label>
                <input id="routerQ" name="q" class="cb-input" placeholder="e.g. pdf/a format required, evidence file too large, no external links" value="${routerQuery != null ? routerQuery : ""}">
            </div>
            <div>
                <label for="routerPlatform" class="block text-sm font-medium text-gray-700 mb-1">Platform</label>
                <select id="routerPlatform" name="platform" class="cb-input">
                    <option value="">All</option>
                    <option value="stripe" selected="${routerPlatformStripe}">Stripe</option>
                    <option value="shopify" selected="${routerPlatformShopify}">Shopify</option>
                </select>
            </div>
            <div class="md:col-span-4 flex justify-end">
                <button type="submit" class="cb-btn cb-btn-primary">Find My Fix Flow</button>
            </div>
        </form>
        @if(routerNoMatch)
            <div class="mt-3 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded p-3">
                <p>No strong match found yet. Try adding platform and exact error words (e.g. <code>pdf/a</code>, <code>duplicate evidence type</code>, <code>no external links</code>).</p>
                @if(routerStartFixUrl != null && !routerStartFixUrl.isBlank())
                    <a href="${routerStartFixUrl}" class="mt-3 inline-block cb-btn cb-btn-primary">Start Free Validation Anyway</a>
                @endif
            </div>
        @endif
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
                <a href="/new?src=guide&platform=${guide.platformSlug()}&guide=${guide.reasonCodeSlug()}" class="cb-btn cb-btn-primary" data-seo-event="guide_start_case_click" data-seo-platform="${guide.platformSlug()}" data-seo-guide="${guide.reasonCodeSlug()}">Start Free Validation</a>
                <a href="${guide.path()}" class="mt-3 inline-block text-sm text-primary-600 hover:underline">See checklist &amp; steps</a>
            </article>
        @endfor
    </section>
`)
