#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, "..");
const OUT_PATH = path.join(ROOT, "src", "main", "resources", "seo", "guides-v1.json");

const STRIPE_SOURCES = [
  "https://docs.stripe.com/disputes/responding",
  "https://docs.stripe.com/disputes/reason-codes-defense-requirements",
];
const STRIPE_FEE_SOURCES = [
  "https://stripe.com/pricing",
  "https://docs.stripe.com/disputes/how-disputes-work",
];
const SHOPIFY_SOURCES = [
  "https://help.shopify.com/en/manual/payments/chargebacks/responding",
  "https://help.shopify.com/en/manual/payments/chargebacks/resolve-chargeback",
];

const REASON_KEY_CHECKS = [
  "Prepare one final file per evidence type and keep filenames reviewer-readable.",
  "Map each attachment to the dispute reason code so reviewers can verify relevance quickly.",
  "Include a timeline that links order, payment, fulfillment, communication, and policy context.",
  "Keep only dispute-relevant excerpts to reduce payload size and reviewer fatigue.",
  "Check format and size constraints before packaging so uploads do not fail at the last step.",
  "Place decisive records first and supporting context second for faster manual review.",
];
const REASON_COMMON_ERRORS = [
  "Evidence is split across too many files, forcing reviewers to reconstruct chronology manually.",
  "Critical records are buried under low-signal screenshots and redundant attachments.",
  "Reason-specific required evidence is missing while optional files dominate the package.",
  "Policy evidence is attached without highlighting the exact section tied to the claim.",
  "Delivery or service logs are present but not connected to customer identity and order facts.",
  "Final package is technically valid but logically weak because narrative and evidence are disconnected.",
];
const REASON_NEXT_STEPS = [
  "Group files by evidence type and merge same-type duplicates into one final reviewer file.",
  "Build a short chronology table before export and ensure each line points to an attached artifact.",
  "Trim non-essential pages and remove repeated screenshots to lower size and complexity.",
  "Reorder artifacts so strongest transaction proof appears first in each evidence category.",
  "Run validation again and fix every blocker before generating the final submission bundle.",
  "Export only when readiness checks pass and missing required evidence warnings are cleared.",
];
const REASON_EXPLANATION_LINES = [
  "The evidence package is organized by submission category and chronological sequence.",
  "Each attachment is mapped to the dispute reason and supports direct reviewer verification.",
  "Order, payment, fulfillment, and communication records are linked with consistent identifiers.",
  "Only dispute-relevant excerpts are included to improve clarity and reduce review friction.",
  "Policy and transactional documents are attached with section-level relevance to the claim.",
  "This structure is intended to minimize upload failures and improve review completeness.",
];
const REASON_FAQ = [
  {
    question: "What causes reason-code evidence rejection most often?",
    answer: "Most rejections come from missing required evidence, weak chronology, and packaging clutter that hides decisive records.",
  },
  {
    question: "How much evidence should be attached?",
    answer: "Attach enough to prove the claim path clearly, but avoid redundant pages that increase size without adding decision value.",
  },
  {
    question: "Should communication logs be full exports?",
    answer: "Use focused excerpts with timestamps and order identifiers, then keep full logs only if they add specific dispute relevance.",
  },
  {
    question: "What should be checked right before export?",
    answer: "Verify required evidence coverage, file format constraints, payload size, and timeline-to-attachment consistency.",
  },
];

const ERROR_KEY_CHECKS = [
  "Identify the exact blocker class first: size, format, page limit, duplicate file type, or unsupported structure.",
  "Normalize files to accepted formats and remove unsupported wrappers before re-upload.",
  "Preserve readability while compressing payload size so evidence quality remains reviewable.",
  "Keep one final file per evidence type and avoid ambiguous naming that breaks reviewer mapping.",
  "Strip external dependencies and include direct artifacts that reviewers can open immediately.",
  "Re-run validation after each fix cycle instead of batching unknown changes blindly.",
];
const ERROR_COMMON_ERRORS = [
  "A single oversized file causes repeated upload failure while other files are already compliant.",
  "PDF or image metadata structures are unsupported even though files open locally.",
  "Duplicate evidence-type uploads create validation blockers that require merge or reclassification.",
  "External links are referenced instead of attaching the underlying proof directly.",
  "Page count or total payload limits are exceeded by low-signal appendices and duplicate exports.",
  "Manual fixes are applied without revalidation, causing new blockers to appear at submission time.",
];
const ERROR_NEXT_STEPS = [
  "Classify blockers by severity and resolve strict upload constraints before content-level tuning.",
  "Re-export problematic files using supported settings and flatten unsupported embedded structures.",
  "Merge duplicate files per evidence type and maintain chronological ordering in merged outputs.",
  "Trim low-value pages first, then apply readable compression to reduce payload size safely.",
  "Re-run validation after each major fix so regressions are caught early.",
  "Finalize only after zero blocking issues remain and required evidence coverage is complete.",
];
const ERROR_EXPLANATION_LINES = [
  "The evidence set was rebuilt using upload-safe formats and validated packaging rules.",
  "All attachments are direct reviewer artifacts without external dependency links.",
  "Duplicate type files were consolidated and ordered to preserve claim chronology.",
  "Payload size was reduced with readability preserved for receipts, logs, and policy excerpts.",
  "Blocking format structures were removed and replaced with compliant evidence documents.",
  "The final package is prepared for consistent submission and manual review flow.",
];
const ERROR_FAQ = [
  {
    question: "Why does upload keep failing after minor edits?",
    answer: "Most failures persist when the root blocker class is unchanged, such as unsupported format wrappers or unresolved size limits.",
  },
  {
    question: "What should be fixed first?",
    answer: "Fix strict blockers first: accepted format, size limits, duplicate-type issues, and unsupported PDF structure.",
  },
  {
    question: "Can I rely on links instead of files?",
    answer: "No. Upload workflows and reviewer tooling are more reliable when the core evidence is attached directly as files.",
  },
  {
    question: "How do I avoid another failure after fixing once?",
    answer: "Use a repeated cycle: fix one blocker class, revalidate immediately, then continue until no blocking errors remain.",
  },
];

const ERROR_ROUTER_PHRASES = {
  "stripe/dispute-countered-fee-manual-15-usd": [
    "stripe dispute countered fee",
    "stripe manual counter fee",
    "stripe dispute fee 15 usd",
    "is stripe dispute counter paid",
  ],
  "stripe/evidence-file-size-limit-4-5mb": [
    "stripe evidence file too large",
    "evidence file size limit 4.5mb",
    "stripe upload exceeds 4.5 mb",
    "chargeback evidence too large stripe",
    "stripe dispute file too large",
  ],
  "stripe/upload-failed-no-external-links": [
    "stripe no external links allowed",
    "upload failed no external links",
    "remove external links from pdf stripe",
    "stripe evidence contains external links",
  ],
  "stripe/merge-multiple-evidence-files": [
    "merge multiple evidence files stripe",
    "one file per evidence type stripe",
    "stripe duplicate evidence file type",
    "stripe combine evidence files",
  ],
  "stripe/invalid-file-format-pdf-jpg-png-only": [
    "stripe invalid file format pdf jpg png only",
    "stripe unsupported file format evidence",
    "heic not supported stripe dispute",
    "webp not supported stripe evidence",
  ],
  "stripe/total-pages-over-limit": [
    "stripe total pages over limit",
    "stripe evidence too many pages",
    "stripe dispute page limit exceeded",
    "stripe upload page count exceeded",
  ],
  "stripe/mastercard-19-page-limit": [
    "mastercard 19 page limit stripe",
    "stripe mastercard evidence 19 pages",
    "mastercard dispute page limit exceeded",
    "stripe mastercard too many pages",
  ],
  "stripe/duplicate-evidence-type-file-error": [
    "duplicate evidence type file error stripe",
    "stripe one file per evidence type",
    "stripe duplicate file same evidence type",
    "stripe cannot upload multiple files same type",
  ],
  "stripe/oversized-pdf-with-low-signal-pages": [
    "stripe oversized pdf evidence",
    "reduce pdf size stripe dispute",
    "stripe compress evidence pdf",
    "stripe trim low signal pages",
  ],
  "stripe/stripe-vs-shopify-evidence-rules-comparison": [
    "stripe vs shopify evidence rules",
    "shopify stripe chargeback evidence difference",
    "compare evidence upload rules stripe shopify",
  ],
  "shopify/pdf-a-format-required-error": [
    "shopify pdf/a format required",
    "shopify pdf a required",
    "pdfa required shopify chargeback",
    "shopify payments pdf/a error",
    "shopify invalid pdf format requires pdf/a",
  ],
  "shopify/evidence-file-too-large-2mb": [
    "shopify evidence file too large 2mb",
    "shopify upload exceeds 2mb",
    "shopify chargeback file too large",
    "shopify compress evidence file under 2mb",
  ],
  "shopify/pdf-portfolio-not-accepted": [
    "shopify pdf portfolio not accepted",
    "shopify embedded pdf attachments not allowed",
    "portfolio pdf rejected shopify",
    "flatten pdf portfolio shopify dispute",
  ],
  "shopify/invalid-file-format-pdf-jpg-png-only": [
    "shopify invalid file format pdf jpg png only",
    "shopify unsupported file format chargeback",
    "shopify heic evidence not supported",
    "shopify webp not supported evidence",
  ],
  "shopify/external-links-not-allowed-error": [
    "shopify external links not allowed",
    "shopify no external links evidence",
    "remove links from pdf shopify chargeback",
    "shopify evidence contains external urls",
  ],
  "shopify/duplicate-evidence-type-file-error": [
    "shopify duplicate evidence type file error",
    "shopify one file per evidence type",
    "shopify duplicate file same evidence type",
    "shopify merge evidence files by type",
  ],
  "shopify/pdf-pages-over-50-error": [
    "shopify pdf pages over 50",
    "shopify pdf exceeds 50 pages",
    "shopify chargeback page limit error",
    "shopify too many pdf pages evidence",
  ],
  "shopify/shopify-payments-total-size-over-4mb": [
    "shopify payments total size over 4mb",
    "shopify chargeback total upload size exceeded",
    "shopify total evidence too large",
    "shopify upload exceeds 4mb total",
  ],
  "shopify/oversized-image-compression-readability-fix": [
    "shopify oversized image compression",
    "shopify compress screenshot for chargeback",
    "shopify image too large evidence",
    "shopify reduce image size readable",
  ],
  "shopify/unsupported-embedded-objects-error": [
    "shopify unsupported embedded objects",
    "shopify embedded object parser failure",
    "shopify unsupported embedded object in pdf",
    "shopify embedded files not accepted",
  ],
};

function phraseSlug(slug) {
  return slug.replaceAll("-", " ");
}

function platformLabel(platformSlug) {
  return platformSlug === "stripe" ? "Stripe" : "Shopify";
}

function sourcesFor(platformSlug, slug) {
  if (platformSlug === "stripe" && slug.includes("countered-fee")) {
    return STRIPE_FEE_SOURCES;
  }
  return platformSlug === "stripe" ? STRIPE_SOURCES : SHOPIFY_SOURCES;
}

function keywordSeed(platformSlug, slug, label) {
  const topic = phraseSlug(slug);
  const normalizedLabel = label.toLowerCase();
  return [
    `${platformSlug} ${topic}`,
    `${platformSlug} upload failed ${topic}`,
    `${platformSlug} ${normalizedLabel} evidence guide`,
    `${platformSlug} dispute evidence ${topic} fix`,
  ];
}

function uniqueStrings(values) {
  const seen = new Set();
  const out = [];
  for (const value of values) {
    const normalized = String(value || "").trim().toLowerCase();
    if (!normalized) continue;
    if (seen.has(normalized)) continue;
    seen.add(normalized);
    out.push(normalized);
  }
  return out;
}

function reasonQueries(platformSlug, slug, label) {
  const topic = phraseSlug(slug);
  const p = platformSlug;
  const base = keywordSeed(platformSlug, slug, label);
  const expanded = [
    `${p} ${topic} checklist`,
    `${p} ${topic} evidence checklist`,
    `${p} ${topic} dispute evidence required`,
    `${p} ${topic} chargeback evidence`,
    `${p} ${topic} evidence upload failed`,
    `${p} ${topic} submission ready`,
  ];
  return uniqueStrings([...base, ...expanded]);
}

function errorQueries(platformSlug, slug, label) {
  const topic = phraseSlug(slug);
  const p = platformSlug;
  const base = keywordSeed(platformSlug, slug, label);
  const generic = [
    `${p} ${topic} error`,
    `${p} ${topic} fix`,
    `${p} ${topic} upload failed`,
    `${p} ${topic} chargeback evidence`,
    `${p} ${topic} submission blocker`,
    `${p} ${topic} troubleshooting`,
  ];
  const specific = ERROR_ROUTER_PHRASES[`${platformSlug}/${slug}`] || [];
  return uniqueStrings([...base, ...generic, ...specific]);
}

function errorCommonErrors(platformSlug, slug) {
  const specific = ERROR_ROUTER_PHRASES[`${platformSlug}/${slug}`] || [];
  const phraseLines = specific.slice(0, 4).map((phrase) => `Raw uploader message often appears as: "${phrase}".`);
  return [...ERROR_COMMON_ERRORS, ...phraseLines];
}

function reasonEntry(platformSlug, slug, label) {
  const pLabel = platformLabel(platformSlug);
  const topicPhrase = phraseSlug(slug);
  return {
    platformSlug,
    slug,
    platformLabel: pLabel,
    reasonCodeLabel: label,
    guideType: "reason",
    title: `${pLabel} ${label} Evidence Checklist for Upload Recovery`,
    metaDescription:
      `Fix ${pLabel} ${topicPhrase} dispute upload failures using reason-mapped evidence structure, stronger chronology, and submission-ready formatting checks before export.`,
    targetSearchQueries: reasonQueries(platformSlug, slug, label),
    keyChecks: REASON_KEY_CHECKS,
    commonErrors: REASON_COMMON_ERRORS,
    nextSteps: REASON_NEXT_STEPS,
    explanationPreviewLines: REASON_EXPLANATION_LINES,
    sourceUrls: sourcesFor(platformSlug, slug),
    faq: REASON_FAQ.map((item) => ({
      question: item.question.replace("reason-code", `${pLabel} ${label}`),
      answer: item.answer,
    })),
  };
}

function errorEntry(platformSlug, slug, label) {
  const pLabel = platformLabel(platformSlug);
  const topicPhrase = phraseSlug(slug);
  const entry = {
    platformSlug,
    slug,
    platformLabel: pLabel,
    reasonCodeLabel: label,
    guideType: "error",
    title: `${pLabel} ${label} Evidence Upload Error Fix Guide`,
    metaDescription:
      `Resolve ${pLabel} ${topicPhrase} upload blockers with precise format checks, payload cleanup, and repeatable re-validation steps before submission export.`,
    targetSearchQueries: errorQueries(platformSlug, slug, label),
    keyChecks: ERROR_KEY_CHECKS,
    commonErrors: errorCommonErrors(platformSlug, slug),
    nextSteps: ERROR_NEXT_STEPS,
    explanationPreviewLines: ERROR_EXPLANATION_LINES,
    sourceUrls: sourcesFor(platformSlug, slug),
    faq: ERROR_FAQ.map((item) => ({ ...item })),
  };

  if (platformSlug === "stripe" && slug === "dispute-countered-fee-manual-15-usd") {
    entry.title = "Stripe Dispute Countered Fee ($15 Manual) ROI and Submission Guide";
    entry.metaDescription =
      "Understand Stripe dispute countered fee exposure for manual submissions and reduce avoidable upload failures before committing to a paid counter workflow.";
    entry.keyChecks = [
      "Confirm whether the dispute will be manually countered and which fees apply in your operating region.",
      "Only proceed to counter once required evidence coverage and packaging readiness are both complete.",
      "Treat upload-failure risk as direct cost exposure when manual countering introduces additional fees.",
      "Prioritize decisive claim-mapped evidence so paid counter attempts are not wasted on formatting blockers.",
      "Review current Stripe pricing policy before each submission cycle because fee policy can change over time.",
      "Document submission decision logic internally for post-case ROI tracking and playbook refinement.",
    ];
    entry.commonErrors = [
      "Countering begins before evidence packaging is ready, causing avoidable paid failure risk.",
      "Merchants treat manual counter flow as no-cost and underestimate per-case downside exposure.",
      "Submission is attempted with unresolved blocker issues, making fee-bearing retries more likely.",
      "Team lacks a pre-counter checklist tying expected recovery to evidence strength and upload readiness.",
      "Fee assumptions are based on outdated community posts instead of current Stripe pricing pages.",
      "Case value is low but manual counter process is still pursued without a minimum ROI threshold.",
    ];
    entry.nextSteps = [
      "Establish a pre-counter gate: required evidence complete, no blocking validation issues, clear chronology.",
      "Check current Stripe pricing policy and internal dispute economics before committing to manual countering.",
      "Use upload recovery flow first so the paid counter step is executed only once with final-ready files.",
      "Track counter attempt outcomes and cost per attempt to calibrate future counter/no-counter decisions.",
      "Create a minimum case-value threshold below which manual countering is not operationally justified.",
      "Reassess policy links monthly and update guidance pages when fee terms or mechanics change.",
    ];
    entry.explanationPreviewLines = [
      "This package was validated for upload readiness before manual counter execution.",
      "Required records are mapped to dispute claims to reduce avoidable paid retries.",
      "Evidence chronology is structured for first-pass reviewer comprehension.",
      "Only decisive, dispute-relevant files are included to avoid unnecessary payload bloat.",
      "Submission follows current fee-aware playbook and documented countering thresholds.",
      "The objective is to reduce avoidable formatting risk before fee-bearing counter actions.",
    ];
    entry.faq = [
      {
        question: "What is a countered fee in Stripe dispute workflow?",
        answer: "Stripe documents additional fee mechanics for manually countered disputes in current pricing and disputes documentation; check live policy before each submission.",
      },
      {
        question: "When should manual countering be avoided?",
        answer: "Avoid manual countering when required evidence is incomplete or package quality is not upload-ready, because retry risk directly harms case ROI.",
      },
      {
        question: "How does upload readiness impact fee-aware ROI?",
        answer: "If countering has incremental cost, each avoidable upload failure increases downside. Readiness checks reduce that avoidable loss path.",
      },
      {
        question: "Which source should be trusted for fee terms?",
        answer: "Use Stripe official pricing and disputes docs as primary source, not stale forum screenshots or third-party summaries.",
      },
    ];
  }

  return entry;
}

function validate(guides) {
  if (guides.length !== 40) {
    throw new Error(`expected 40 guides, got ${guides.length}`);
  }
  const seen = new Set();
  const titleSet = new Set();
  const metaSet = new Set();

  for (const guide of guides) {
    const key = `${guide.platformSlug}/${guide.slug}`;
    if (seen.has(key)) {
      throw new Error(`duplicate slug key: ${key}`);
    }
    seen.add(key);

    if (guide.title.length < 35 || guide.title.length > 90) {
      throw new Error(`title length out of range (${guide.title.length}): ${guide.title}`);
    }
    if (guide.metaDescription.length < 90 || guide.metaDescription.length > 190) {
      throw new Error(`meta length out of range (${guide.metaDescription.length}): ${guide.metaDescription}`);
    }
    if (titleSet.has(guide.title)) throw new Error(`duplicate title: ${guide.title}`);
    if (metaSet.has(guide.metaDescription)) throw new Error(`duplicate meta: ${guide.metaDescription}`);
    titleSet.add(guide.title);
    metaSet.add(guide.metaDescription);

    if (guide.targetSearchQueries.length < 4) throw new Error(`missing targetSearchQueries depth: ${key}`);
    if (guide.keyChecks.length < 5) throw new Error(`missing keyChecks depth: ${key}`);
    if (guide.commonErrors.length < 5) throw new Error(`missing commonErrors depth: ${key}`);
    if (guide.nextSteps.length < 5) throw new Error(`missing nextSteps depth: ${key}`);
    if (guide.explanationPreviewLines.length < 5) throw new Error(`missing explanationPreviewLines depth: ${key}`);
    if (guide.faq.length < 4) throw new Error(`missing faq depth: ${key}`);
  }
}

function main() {
  const stripeReasonSpecs = [
    ["fraudulent", "Fraudulent"],
    ["product-not-received", "Product Not Received"],
    ["product-unacceptable", "Product Unacceptable"],
    ["credit-not-processed", "Credit Not Processed"],
    ["subscription-cancelled", "Subscription Cancelled"],
    ["duplicate", "Duplicate"],
    ["unrecognized", "Unrecognized"],
    ["not-as-described", "Not As Described"],
    ["canceled-order", "Canceled Order"],
    ["general", "General"],
  ];
  const shopifyReasonSpecs = [
    ["fraudulent", "Fraudulent"],
    ["item-not-received", "Item Not Received"],
    ["not-as-described", "Not As Described"],
    ["cancelled-order", "Cancelled Order"],
    ["duplicate", "Duplicate"],
    ["credit-not-processed", "Credit Not Processed"],
    ["unrecognized", "Unrecognized"],
    ["subscription-cancelled", "Subscription Cancelled"],
    ["product-unacceptable", "Product Unacceptable"],
    ["general", "General"],
  ];
  const stripeErrorSpecs = [
    ["dispute-countered-fee-manual-15-usd", "Dispute Countered Fee Manual 15 USD"],
    ["evidence-file-size-limit-4-5mb", "Evidence File Size Limit 4.5MB"],
    ["upload-failed-no-external-links", "Upload Failed No External Links"],
    ["merge-multiple-evidence-files", "Merge Multiple Evidence Files"],
    ["invalid-file-format-pdf-jpg-png-only", "Invalid File Format PDF JPG PNG Only"],
    ["total-pages-over-limit", "Total Pages Over Limit"],
    ["mastercard-19-page-limit", "Mastercard 19 Page Limit"],
    ["duplicate-evidence-type-file-error", "Duplicate Evidence Type File Error"],
    ["oversized-pdf-with-low-signal-pages", "Oversized PDF With Low Signal Pages"],
    ["stripe-vs-shopify-evidence-rules-comparison", "Stripe Vs Shopify Evidence Rules Comparison"],
  ];
  const shopifyErrorSpecs = [
    ["pdf-a-format-required-error", "PDF A Format Required Error"],
    ["evidence-file-too-large-2mb", "Evidence File Too Large 2MB"],
    ["pdf-portfolio-not-accepted", "PDF Portfolio Not Accepted"],
    ["invalid-file-format-pdf-jpg-png-only", "Invalid File Format PDF JPG PNG Only"],
    ["external-links-not-allowed-error", "External Links Not Allowed Error"],
    ["duplicate-evidence-type-file-error", "Duplicate Evidence Type File Error"],
    ["pdf-pages-over-50-error", "PDF Pages Over 50 Error"],
    ["shopify-payments-total-size-over-4mb", "Shopify Payments Total Size Over 4MB"],
    ["oversized-image-compression-readability-fix", "Oversized Image Compression Readability Fix"],
    ["unsupported-embedded-objects-error", "Unsupported Embedded Objects Error"],
  ];

  const guides = [];
  for (const [slug, label] of stripeReasonSpecs) guides.push(reasonEntry("stripe", slug, label));
  for (const [slug, label] of shopifyReasonSpecs) guides.push(reasonEntry("shopify", slug, label));
  for (const [slug, label] of stripeErrorSpecs) guides.push(errorEntry("stripe", slug, label));
  for (const [slug, label] of shopifyErrorSpecs) guides.push(errorEntry("shopify", slug, label));

  validate(guides);

  const catalog = {
    version: "2026-03-07",
    guides,
  };

  fs.mkdirSync(path.dirname(OUT_PATH), { recursive: true });
  fs.writeFileSync(OUT_PATH, JSON.stringify(catalog, null, 2), "utf-8");
  console.log(`generated ${guides.length} guides -> ${OUT_PATH}`);
}

main();
