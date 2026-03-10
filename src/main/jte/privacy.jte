@template.layout(title = "Privacy Policy", content = @`
    <div class="max-w-3xl mx-auto py-8">
        <a href="/" class="inline-flex items-center text-sm font-medium text-gray-500 hover:text-gray-900 transition-colors mb-6">
            <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path></svg>
            Back to Home
        </a>
        
        <div class="cb-card prose prose-indigo max-w-none">
            <h1 class="text-3xl font-bold tracking-tight text-neutral-900 mb-2">Privacy Policy</h1>
            <p class="text-gray-500 text-sm mb-8 pb-4 border-b border-gray-100">Last updated: 2026-03-09</p>
            
            <div class="space-y-8 text-gray-600 leading-relaxed">
                <div>
                    <h2 class="text-lg font-bold text-gray-900 mb-2">Data Collected</h2>
                    <p>We store case metadata, uploaded evidence files, validation and automatic-fix logs, and minimal payment metadata required for service operation.</p>
                </div>
                
                <div>
                    <h2 class="text-lg font-bold text-gray-900 mb-2">Retention and Deletion</h2>
                    <p>Cases and uploaded evidence files are retained for <strong>7 days by default</strong>. If a due date is set, retention may extend to due date plus a safety buffer. After expiry, case files and metadata are automatically permanently deleted from our servers. Users can also manually delete their case immediately from the dashboard at any time.</p>
                </div>
                
                <div>
                    <h2 class="text-lg font-bold text-gray-900 mb-2">Use of Data</h2>
                    <p>Your uploaded files and case data are used <strong>exclusively</strong> to provide validation, auto-fix transformations, and the generation of formatted evidence artifacts. Your data is not sold or used for AI training.</p>
                </div>
                
                <div>
                    <h2 class="text-lg font-bold text-gray-900 mb-2">Payment Data</h2>
                    <p>Payments are processed by our configured provider, currently Lemon Squeezy for production beta sales. We do not process, parse, or store raw credit card details on our infrastructure.</p>
                </div>
                
                <div>
                    <h2 class="text-lg font-bold text-gray-900 mb-2">Security</h2>
                    <p>Access to individual case resources requires the exact case token verification. Service-level access controls are strictly applied to all underlying cloud storage bins.</p>
                </div>

                <div>
                    <h2 class="text-lg font-bold text-gray-900 mb-2">Contact</h2>
                    <p>Privacy or deletion questions can be sent to <a href="mailto:shinhyeok22@gmail.com">shinhyeok22@gmail.com</a>.</p>
                </div>
            </div>
        </div>
    </div>
`)
