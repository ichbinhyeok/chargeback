@import com.example.demo.dispute.web.GuidePageView
@param GuidePageView guide
@param String canonicalUrl
@param String metaDescription

@template.layout(
    title = guide.title() + " | Chargeback Builder",
    canonicalUrl = canonicalUrl,
    metaDescription = metaDescription,
    content = @`
    <article class="max-w-4xl">
        <div class="text-sm mb-4">
            <a href="/guides" class="text-primary-600 hover:underline">Guides</a>
            <span class="text-gray-400"> / </span>
            <a href="${guide.platformPath()}" class="text-primary-600 hover:underline">${guide.platformLabel()}</a>
            <span class="text-gray-400"> / </span>
            <span class="text-gray-600">${guide.reasonCodeLabel()}</span>
        </div>

        <p class="text-xs uppercase tracking-wide text-primary-600 font-semibold mb-2">${guide.platformLabel()} - ${guide.reasonCodeLabel()}</p>
        <h1 class="text-3xl font-bold text-gray-900 mb-3">${guide.title()}</h1>
        <p class="text-gray-600 mb-8">${guide.metaDescription()}</p>

        <section class="cb-card mb-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-3">Key Checks</h2>
            <ul class="list-disc pl-5 space-y-2 text-sm text-gray-700">
                @for(String check : guide.keyChecks())
                    <li>${check}</li>
                @endfor
            </ul>
        </section>

        <section class="cb-card mb-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-3">Common Failures</h2>
            <ul class="list-disc pl-5 space-y-2 text-sm text-gray-700">
                @for(String err : guide.commonErrors())
                    <li>${err}</li>
                @endfor
            </ul>
        </section>

        <section class="cb-card mb-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-3">Next Actions</h2>
            <ol class="list-decimal pl-5 space-y-2 text-sm text-gray-700">
                @for(String step : guide.nextSteps())
                    <li>${step}</li>
                @endfor
            </ol>
        </section>

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
            <p>This guide is informational and focuses on submission formatting. It is not legal advice and does not guarantee dispute outcomes.</p>
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
