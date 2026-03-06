@param String canonicalUrl
@param String metaDescription
@param String robotsMeta

@template.layout(
    title = "SEO KPI Dashboard | Chargeback Builder",
    canonicalUrl = canonicalUrl,
    metaDescription = metaDescription,
    robotsMeta = robotsMeta,
    content = @`
    <section class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900 mb-2">SEO KPI Dashboard</h1>
        <p class="text-gray-600">Guide page traffic and funnel metrics (<code>guide_view -&gt; guide_start_case_click -&gt; new_case_view_from_guide -&gt; case_created_from_guide -&gt; guide_export_click</code>).</p>
    </section>

    <section class="cb-card mb-6">
        <div class="flex flex-wrap items-center gap-3">
            <label for="days" class="text-sm font-medium text-gray-700">Window (days)</label>
            <select id="days" class="cb-input w-28">
                <option value="7">7</option>
                <option value="14">14</option>
                <option value="30" selected>30</option>
                <option value="60">60</option>
                <option value="90">90</option>
            </select>
            <button id="refreshKpi" class="cb-btn cb-btn-primary">Refresh</button>
            <span id="kpiStatus" class="text-sm text-gray-500"></span>
        </div>
    </section>

    <section class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">Guide Views</p>
            <p id="guideViewCount" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">Start Case Clicks</p>
            <p id="startCaseClickCount" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">New Case Views (from Guide)</p>
            <p id="newCaseViewCount" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
    </section>

    <section class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">Case Created (from Guide)</p>
            <p id="caseCreatedCount" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">Export Clicks (from Guide)</p>
            <p id="exportClickCount" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
    </section>

    <section class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">Router No-Match</p>
            <p id="routerNoMatchCount" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">No-Match -> New Case View</p>
            <p id="routerToNewRate" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">No-Match -> Case Created</p>
            <p id="routerToCreatedRate" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
    </section>

    <section class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">Guide -> Start Case CTR</p>
            <p id="guideToStartRate" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">Start Case -> New Case View Rate</p>
            <p id="startToNewRate" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">New Case View -> Case Created Rate</p>
            <p id="newToCreatedRate" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
        <article class="cb-card">
            <p class="text-xs uppercase text-gray-500 tracking-wide">Case Created -> Export Click Rate</p>
            <p id="createdToExportRate" class="text-2xl font-bold text-gray-900 mt-1">-</p>
        </article>
    </section>

    <section class="cb-card mb-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-3">Top Guides By View</h2>
        <div class="overflow-x-auto">
            <table class="cb-table">
                <thead>
                <tr>
                    <th>Platform</th>
                    <th>Guide Slug</th>
                    <th>Views</th>
                </tr>
                </thead>
                <tbody id="topGuidesBody">
                <tr><td colspan="3" class="text-gray-500">No data</td></tr>
                </tbody>
            </table>
        </div>
    </section>

    <section class="cb-card mb-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-3">Guide Funnel (Top by View)</h2>
        <div class="overflow-x-auto">
            <table class="cb-table">
                <thead>
                <tr>
                    <th>Platform</th>
                    <th>Guide Slug</th>
                    <th>Views</th>
                    <th>Starts</th>
                    <th>Created</th>
                    <th>Exports</th>
                    <th>View -&gt; Created</th>
                    <th>Created -&gt; Export</th>
                </tr>
                </thead>
                <tbody id="guideFunnelBody">
                <tr><td colspan="8" class="text-gray-500">No data</td></tr>
                </tbody>
            </table>
        </div>
    </section>

    <section class="cb-card mb-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-3">Priority Guides To Improve</h2>
        <div class="overflow-x-auto">
            <table class="cb-table">
                <thead>
                <tr>
                    <th>Platform</th>
                    <th>Guide Slug</th>
                    <th>Views</th>
                    <th>View -&gt; Created</th>
                    <th>Created -&gt; Export</th>
                </tr>
                </thead>
                <tbody id="priorityGuidesBody">
                <tr><td colspan="5" class="text-gray-500">No data</td></tr>
                </tbody>
            </table>
        </div>
    </section>

    <section class="cb-card mb-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-3">Router No-Match Opportunities</h2>
        <p class="text-sm text-gray-600 mb-3">High-frequency queries that failed routing. Use these to create new pSEO pages.</p>
        <div class="overflow-x-auto">
            <table class="cb-table">
                <thead>
                <tr>
                    <th>Platform</th>
                    <th>Query</th>
                    <th>Count</th>
                    <th>Suggested Slug</th>
                    <th>Suggested Title</th>
                </tr>
                </thead>
                <tbody id="routerOpportunitiesBody">
                <tr><td colspan="5" class="text-gray-500">No data</td></tr>
                </tbody>
            </table>
        </div>
    </section>

    <section class="cb-card">
        <h2 class="text-lg font-semibold text-gray-900 mb-3">Event Counts</h2>
        <div class="overflow-x-auto">
            <table class="cb-table">
                <thead>
                <tr>
                    <th>Event</th>
                    <th>Count</th>
                </tr>
                </thead>
                <tbody id="eventCountsBody">
                <tr><td colspan="2" class="text-gray-500">No data</td></tr>
                </tbody>
            </table>
        </div>
    </section>

    <script>
        (function () {
            const daysSelect = document.getElementById('days');
            const refreshButton = document.getElementById('refreshKpi');
            const statusNode = document.getElementById('kpiStatus');

            function formatPercent(value) {
                const num = Number(value || 0);
                return (num * 100).toFixed(1) + '%';
            }

            function setText(id, text) {
                const node = document.getElementById(id);
                if (!node) return;
                node.textContent = text;
            }

            function renderRows(bodyId, rows, renderRow, emptyHtml) {
                const body = document.getElementById(bodyId);
                if (!body) return;
                if (!rows || rows.length === 0) {
                    body.innerHTML = emptyHtml;
                    return;
                }
                body.innerHTML = rows.map(renderRow).join('');
            }

            async function loadKpi() {
                const days = daysSelect.value;
                statusNode.textContent = 'Loading...';
                try {
                    const response = await fetch('/api/seo/kpi?days=' + encodeURIComponent(days), { cache: 'no-store' });
                    if (!response.ok) {
                        throw new Error('kpi request failed');
                    }
                    const data = await response.json();
                    setText('guideViewCount', String(data.guideViewCount || 0));
                    setText('startCaseClickCount', String(data.startCaseClickCount || 0));
                    setText('newCaseViewCount', String(data.newCaseViewFromGuideCount || 0));
                    setText('caseCreatedCount', String(data.caseCreatedFromGuideCount || 0));
                    setText('exportClickCount', String(data.guideExportClickCount || 0));
                    setText('routerNoMatchCount', String(data.guideRouterNoMatchCount || 0));
                    setText('guideToStartRate', formatPercent(data.guideToStartCaseClickRate));
                    setText('startToNewRate', formatPercent(data.startCaseClickToNewCaseViewRate));
                    setText('newToCreatedRate', formatPercent(data.newCaseViewToCaseCreatedRate));
                    setText('createdToExportRate', formatPercent(data.caseCreatedToGuideExportClickRate));
                    setText('routerToNewRate', formatPercent(data.routerNoMatchToNewCaseViewRate));
                    setText('routerToCreatedRate', formatPercent(data.routerNoMatchToCaseCreatedRate));

                    renderRows('topGuidesBody', data.topGuidesByView || [], function (row) {
                        return '<tr><td>' + row.platform + '</td><td>' + row.guideSlug + '</td><td>' + row.guideViews + '</td></tr>';
                    }, '<tr><td colspan="3" class="text-gray-500">No data</td></tr>');

                    renderRows('guideFunnelBody', data.guideFunnels || [], function (row) {
                        return '<tr>'
                                + '<td>' + row.platform + '</td>'
                                + '<td>' + row.guideSlug + '</td>'
                                + '<td>' + row.guideViews + '</td>'
                                + '<td>' + row.startCaseClicks + '</td>'
                                + '<td>' + row.caseCreated + '</td>'
                                + '<td>' + row.exportClicks + '</td>'
                                + '<td>' + formatPercent(row.viewToCreatedRate) + '</td>'
                                + '<td>' + formatPercent(row.createdToExportRate) + '</td>'
                                + '</tr>';
                    }, '<tr><td colspan="8" class="text-gray-500">No data</td></tr>');

                    renderRows('priorityGuidesBody', data.priorityGuidesToImprove || [], function (row) {
                        return '<tr>'
                                + '<td>' + row.platform + '</td>'
                                + '<td>' + row.guideSlug + '</td>'
                                + '<td>' + row.guideViews + '</td>'
                                + '<td>' + formatPercent(row.viewToCreatedRate) + '</td>'
                                + '<td>' + formatPercent(row.createdToExportRate) + '</td>'
                                + '</tr>';
                    }, '<tr><td colspan="5" class="text-gray-500">No data</td></tr>');

                    renderRows('routerOpportunitiesBody', data.routerOpportunities || [], function (row) {
                        return '<tr>'
                                + '<td>' + row.platform + '</td>'
                                + '<td>' + row.queryText + '</td>'
                                + '<td>' + row.count + '</td>'
                                + '<td><code>' + row.suggestedSlug + '</code></td>'
                                + '<td>' + row.suggestedTitle + '</td>'
                                + '</tr>';
                    }, '<tr><td colspan="5" class="text-gray-500">No data</td></tr>');

                    renderRows('eventCountsBody', data.eventCounts || [], function (row) {
                        return '<tr><td>' + row.eventName + '</td><td>' + row.count + '</td></tr>';
                    }, '<tr><td colspan="2" class="text-gray-500">No data</td></tr>');

                    statusNode.textContent = 'Updated';
                } catch (error) {
                    statusNode.textContent = 'Failed to load KPI';
                }
            }

            refreshButton.addEventListener('click', loadKpi);
            document.addEventListener('DOMContentLoaded', loadKpi);
        })();
    </script>
`)
