#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, "..");
const OUT_PATH = path.join(ROOT, "src", "main", "resources", "seo", "guides-v1.json");
const MIN_GUIDE_FLOOR = 40;

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

function topicReasonEntry(spec) {
  return {
    platformSlug: spec.platformSlug,
    slug: spec.slug,
    platformLabel: platformLabel(spec.platformSlug),
    reasonCodeLabel: spec.reasonCodeLabel,
    guideType: "reason",
    title: spec.title,
    metaDescription: spec.metaDescription,
    targetSearchQueries: uniqueStrings(spec.targetSearchQueries),
    keyChecks: spec.keyChecks,
    commonErrors: spec.commonErrors,
    nextSteps: spec.nextSteps,
    explanationPreviewLines: spec.explanationPreviewLines,
    sourceUrls: spec.sourceUrls,
    faq: spec.faq,
  };
}

const EXPANSION_REASON_SPECS = [
  {
    platformSlug: "stripe",
    slug: "inquiry-vs-chargeback-workflow",
    reasonCodeLabel: "Inquiry vs Chargeback Workflow",
    title: "Stripe Inquiry vs Chargeback Workflow and Evidence Response Guide",
    metaDescription:
      "Handle Stripe inquiries and chargebacks without missing critical response windows by mapping status changes, evidence preparation timing, and submission checkpoints in one workflow.",
    targetSearchQueries: [
      "stripe inquiry vs chargeback",
      "stripe warning_needs_response meaning",
      "stripe dispute workflow inquiry to chargeback",
      "stripe evidence response timeline",
      "how to respond to stripe inquiry",
      "stripe dispute status open won lost",
    ],
    keyChecks: [
      "Identify whether the case is an inquiry or a formal chargeback before preparing the submission path.",
      "Track status transitions in Dashboard so inquiry responses do not slip into avoidable escalation.",
      "Prepare evidence package early because chargebacks generally allow only one submission opportunity.",
      "Confirm due-by timestamps and internal owner assignment before the case enters final response window.",
      "Keep order facts, customer communication, and fulfillment records mapped to a single case chronology.",
      "Submit through the same operational checklist for both inquiry and chargeback to reduce handoff errors.",
    ],
    commonErrors: [
      "Teams treat inquiries as low priority and miss fast response windows tied to card-network review.",
      "Evidence is prepared only after escalation, leaving too little time for quality checks.",
      "Case owners confuse inquiry notes with formal dispute evidence fields and upload incomplete files.",
      "Status changes in Dashboard are not monitored daily, causing deadline drift across shared inboxes.",
      "Chronology is built from memory instead of exported transaction logs, creating contradictory narratives.",
      "The same case is edited by multiple operators without a single source of truth for final submission.",
    ],
    nextSteps: [
      "Create a triage step that classifies every new case as inquiry, chargeback, or unknown within one business day.",
      "Attach a due-date tracker and escalation owner to each case at intake.",
      "Build draft evidence bundle before final status change so only polishing remains near deadline.",
      "Run a packaging checklist before submit to confirm file rules, chronology coherence, and required evidence coverage.",
      "Document post-case outcomes by status path so inquiry-to-chargeback leaks can be reduced next cycle.",
      "Review Stripe dispute lifecycle docs monthly and update SOP wording when status semantics change.",
    ],
    explanationPreviewLines: [
      "This case was triaged under inquiry-vs-chargeback workflow with due-date ownership assigned.",
      "Evidence chronology is prepared in advance to avoid last-minute escalation failures.",
      "Submission artifacts are mapped to transaction and communication records with consistent identifiers.",
      "Final package follows one-response discipline for formal chargeback handling.",
      "Status transitions and timestamps are captured to support reviewer context and internal auditability.",
      "The objective is to prevent avoidable timeline misses while preserving evidence clarity.",
    ],
    sourceUrls: [
      "https://docs.stripe.com/disputes/how-disputes-work",
      "https://docs.stripe.com/disputes/responding",
    ],
    faq: [
      {
        question: "What is the operational difference between an inquiry and a chargeback in Stripe?",
        answer: "An inquiry is a pre-dispute path with shorter response windows, while chargebacks move to a formal evidence submission and decision cycle.",
      },
      {
        question: "Can we wait to prepare evidence until a formal chargeback appears?",
        answer: "That increases miss risk. Building evidence earlier improves deadline safety and submission quality when escalation occurs.",
      },
      {
        question: "How many times can evidence be submitted for one chargeback case?",
        answer: "Stripe documents chargebacks as generally allowing one evidence submission opportunity, so first-pass quality matters.",
      },
      {
        question: "Which Stripe pages should operations monitor regularly?",
        answer: "The dispute lifecycle and responding docs should be reviewed regularly because status behavior and guidance can evolve.",
      },
    ],
  },
  {
    platformSlug: "stripe",
    slug: "response-deadline-evidence-window",
    reasonCodeLabel: "Response Deadline and Evidence Window",
    title: "Stripe Response Deadline and Evidence Submission Window Guide",
    metaDescription:
      "Plan Stripe dispute evidence delivery around strict due-by windows by combining deadline governance, pre-submit validation, and timeline-safe escalation controls.",
    targetSearchQueries: [
      "stripe dispute response deadline",
      "stripe due by date dispute evidence",
      "stripe inquiry response window days",
      "stripe chargeback evidence submission deadline",
      "how long to respond stripe dispute",
      "stripe deadline missed dispute",
    ],
    keyChecks: [
      "Capture Stripe due-by timestamps at case creation and mirror them into internal reminders.",
      "Separate inquiry windows from chargeback windows so each queue follows correct urgency rules.",
      "Freeze non-essential edits near deadline and prioritize blocker removal plus required evidence coverage.",
      "Use freshness checks before payment or export to avoid stale-validation submissions near cutoff.",
      "Keep one operator responsible for final submit to avoid duplicate or conflicting uploads.",
      "Record the final submission timestamp and artifact checksum for post-incident traceability.",
    ],
    commonErrors: [
      "Teams rely on memory for due dates and miss dashboard reminders during workload spikes.",
      "Operators keep editing files after validation, then submit stale evidence outputs without rerun.",
      "Deadline triage mixes inquiry and chargeback queues, causing mis-prioritized response order.",
      "Final submit is delayed by unresolved format blockers that should have been fixed earlier.",
      "No one owns final submission step, so multiple handoffs consume the remaining window.",
      "Submission timestamp is undocumented, making deadline disputes difficult to reconstruct later.",
    ],
    nextSteps: [
      "Implement T-72h, T-24h, and T-4h deadline checkpoints with explicit case owner handoff rules.",
      "Gate final submit on fresh validation plus required evidence completion.",
      "Prepare fallback compressed artifacts before deadline so large-file failures can be recovered quickly.",
      "Escalate unresolved blockers at least one business day before due-by cutoff.",
      "Archive pre-submit and submitted artifact manifests for incident review.",
      "Run weekly SLA audit on missed or near-missed disputes and tune staffing accordingly.",
    ],
    explanationPreviewLines: [
      "This submission followed deadline-governed workflow with explicit due-by checkpoints.",
      "Evidence package was validated in a fresh state immediately before final submit.",
      "Required evidence coverage was confirmed before export and upload.",
      "Final artifact set is timestamped and mapped to a single submission owner.",
      "Fallback packaging path was prepared to mitigate late-stage format or size failures.",
      "The workflow is designed to reduce avoidable misses on strict dispute response windows.",
    ],
    sourceUrls: [
      "https://docs.stripe.com/disputes/how-disputes-work",
      "https://docs.stripe.com/disputes/responding",
      "https://docs.stripe.com/disputes/withdrawing",
    ],
    faq: [
      {
        question: "How strict are Stripe dispute due dates?",
        answer: "Due-by timestamps are strict and managed through Dashboard status and network timelines, so late submission risks automatic loss.",
      },
      {
        question: "Should validation be rerun right before submit?",
        answer: "Yes. Any evidence change after validation can make prior results stale and increase rejection risk.",
      },
      {
        question: "Can deadline governance be automated?",
        answer: "Yes, with reminder milestones, owner assignment, and export gating tied to fresh validation.",
      },
      {
        question: "What should be captured for audit after submission?",
        answer: "Capture due-by value, submit timestamp, artifact manifest, and case owner to support post-case review.",
      },
    ],
  },
  {
    platformSlug: "stripe",
    slug: "dispute-fee-countered-fee-economics",
    reasonCodeLabel: "Dispute Fee and Countered Fee Economics",
    title: "Stripe Dispute Fee and Countered Fee Decision Playbook",
    metaDescription:
      "Decide when to contest Stripe disputes using fee-aware evidence readiness rules, so low-ROI cases do not consume manual effort and avoidable formatting risk.",
    targetSearchQueries: [
      "stripe dispute fee",
      "stripe countered fee",
      "stripe dispute fee returned if won",
      "stripe should i contest dispute",
      "stripe chargeback fee economics",
      "stripe pricing dispute manual counter",
    ],
    keyChecks: [
      "Check current Stripe pricing and region-specific fee policy before deciding to counter.",
      "Estimate expected recovery against fees and internal handling time for each case value band.",
      "Counter only when required evidence is complete and packaging blockers are cleared.",
      "Apply minimum case-value threshold to avoid fee-negative manual efforts.",
      "Track fee outcomes by reason code to improve future counter/no-counter decisions.",
      "Revalidate the package after any fee-driven scope change to avoid stale exports.",
    ],
    commonErrors: [
      "Teams counter low-value disputes without fee-aware threshold and lose operational margin.",
      "Policy assumptions are based on old screenshots instead of current Stripe pricing docs.",
      "Cases are countered before evidence readiness is confirmed, creating avoidable paid retries.",
      "No post-case ROI tracking exists, so teams repeat unprofitable contest patterns.",
      "Operators treat all reason codes equally even when evidence recoverability differs.",
      "Economic decision and technical readiness are separated, causing misaligned go/no-go calls.",
    ],
    nextSteps: [
      "Create a fee-aware decision matrix by case value, reason code, and evidence strength.",
      "Add readiness gate before countering: fresh validation, required evidence complete, chronology complete.",
      "Review Stripe pricing references monthly and update SOP if fee mechanics change.",
      "Log recoveries, losses, and effort hours for each contested dispute.",
      "Train team on no-counter scenarios where economics are predictably negative.",
      "Use quarterly retrospective to recalibrate thresholds with real dispute outcomes.",
    ],
    explanationPreviewLines: [
      "This dispute was reviewed under fee-aware counter decision criteria.",
      "Evidence readiness checks were completed before any fee-bearing action.",
      "Case value and expected recovery were compared against current dispute handling costs.",
      "Submission package quality controls were applied to reduce avoidable paid retries.",
      "Decision rationale is logged for post-case ROI and process improvement analysis.",
      "The goal is operationally disciplined dispute response, not blanket contest behavior.",
    ],
    sourceUrls: [
      "https://stripe.com/pricing",
      "https://docs.stripe.com/disputes/how-disputes-work",
      "https://docs.stripe.com/disputes/responding",
    ],
    faq: [
      {
        question: "Where should Stripe dispute fee terms be checked?",
        answer: "Use Stripe official pricing and disputes documentation as the policy source of truth for your region.",
      },
      {
        question: "Is every dispute worth contesting?",
        answer: "No. A fee-aware threshold helps avoid cases where handling cost exceeds expected recovery.",
      },
      {
        question: "Why tie economics to evidence readiness?",
        answer: "Because weak or non-compliant packages increase avoidable retries and reduce expected ROI.",
      },
      {
        question: "How often should counter thresholds be updated?",
        answer: "Revisit thresholds periodically with actual outcome data and any pricing-policy updates.",
      },
    ],
  },
  {
    platformSlug: "stripe",
    slug: "dispute-rate-monitoring-program-readiness",
    reasonCodeLabel: "Dispute Rate Monitoring Readiness",
    title: "Stripe Dispute Rate Monitoring Program Readiness Guide",
    metaDescription:
      "Prepare for Stripe network monitoring pressure with dispute-rate measurement discipline, reason-level trend tracking, and escalation playbooks tied to evidence quality.",
    targetSearchQueries: [
      "stripe dispute rate",
      "stripe monitoring programs chargebacks",
      "stripe excessive dispute ratio",
      "stripe how to measure disputes",
      "card network dispute monitoring program",
      "reduce stripe dispute rate workflow",
    ],
    keyChecks: [
      "Track dispute counts and payment volume with consistent denominator and reporting period.",
      "Segment dispute trends by product, reason code, and acquisition channel for actionable diagnosis.",
      "Review Stripe measuring guidance and card-network monitoring references together.",
      "Prioritize high-frequency reason clusters where evidence quality and policy clarity are weak.",
      "Define escalation ownership for ratio spikes before network program pressure intensifies.",
      "Use prevention and response metrics jointly so operations do not optimize one side only.",
    ],
    commonErrors: [
      "Teams report raw dispute counts without normalized rate, hiding growth in underlying risk.",
      "Ratio spikes are reviewed monthly only, missing early intervention windows.",
      "No reason-code segmentation is used, so mitigation actions remain generic and ineffective.",
      "Monitoring terms are misunderstood and network policy updates are not tracked.",
      "Prevention and response teams operate separately with no shared KPI baseline.",
      "Improvement efforts focus on tooling changes without process-level root cause analysis.",
    ],
    nextSteps: [
      "Build weekly dispute-rate dashboard with trendline, channel segmentation, and reason breakdown.",
      "Set alert thresholds and assign escalation owner for sudden rate acceleration.",
      "Audit top repeat dispute reasons and map them to specific prevention controls.",
      "Create monthly review between risk, support, and payment operations teams.",
      "Run targeted content and evidence-playbook updates for high-volume dispute clusters.",
      "Track post-mitigation rate movement and close loops with documented action outcomes.",
    ],
    explanationPreviewLines: [
      "This case is linked to dispute-rate monitoring workflow and reason-level trend analysis.",
      "Evidence quality controls are aligned with high-frequency dispute categories.",
      "Operational response includes measured escalation thresholds and ownership rules.",
      "Risk and operations metrics are reviewed together to avoid blind spots.",
      "Policy-aware handling is designed for sustained rate reduction over time.",
      "The workflow prioritizes repeatable prevention and response discipline.",
    ],
    sourceUrls: [
      "https://docs.stripe.com/disputes/measuring",
      "https://docs.stripe.com/disputes/monitoring-programs",
      "https://docs.stripe.com/disputes/how-disputes-work",
    ],
    faq: [
      {
        question: "Why is dispute-rate tracking better than raw count tracking?",
        answer: "Rate normalizes disputes against transaction volume, making risk trend changes visible across growth periods.",
      },
      {
        question: "What should be segmented first?",
        answer: "Segment by reason code and channel first because that usually reveals the highest-leverage mitigation work.",
      },
      {
        question: "How often should monitoring data be reviewed?",
        answer: "Weekly review is typical for operational control, with deeper monthly retrospectives for program changes.",
      },
      {
        question: "Does better evidence handling reduce monitoring risk directly?",
        answer: "It helps by improving response quality, while prevention-side controls are still needed to reduce inflow.",
      },
    ],
  },
  {
    platformSlug: "stripe",
    slug: "liability-shift-3ds-inquiry-no-reply-risk",
    reasonCodeLabel: "3DS Liability Shift and Inquiry Risk",
    title: "Stripe 3DS Liability Shift and Inquiry No-Reply Risk Guide",
    metaDescription:
      "Reduce avoidable losses on 3DS-related disputes by handling inquiry responses correctly and preserving authentication evidence when liability-shift paths apply.",
    targetSearchQueries: [
      "stripe 3ds liability shift dispute",
      "stripe inquiry no reply means chargeback",
      "stripe warning_needs_response 3ds",
      "stripe authenticated transaction dispute evidence",
      "stripe 3d secure inquiry response",
      "stripe dispute liability shift workflow",
    ],
    keyChecks: [
      "Confirm whether 3DS authentication succeeded and how liability shift applies to the specific card brand context.",
      "Do not ignore inquiry statuses even when liability shift appears favorable.",
      "Retain authentication logs and payment flow evidence alongside order and fulfillment records.",
      "Map inquiry response content to issuer questions before escalation occurs.",
      "Escalate unresolved 3DS evidence gaps before due-by window gets compressed.",
      "Keep policy notes current because liability interpretation can vary by network and dispute path.",
    ],
    commonErrors: [
      "Teams assume liability shift guarantees no action and skip inquiry response steps.",
      "Authentication records are not preserved in a reviewer-readable package.",
      "Case notes mention 3DS success but provide no concrete supporting artifacts.",
      "Operators respond generically without addressing issuer inquiry prompts.",
      "No process exists to verify whether transaction truly qualified for expected liability path.",
      "Late escalation leaves no time to recover missing authentication evidence.",
    ],
    nextSteps: [
      "Add 3DS evidence checklist fields to intake for all card-presented fraud disputes.",
      "Set inquiry response SLA that triggers before escalation to formal chargeback.",
      "Bundle authentication, order, and fulfillment artifacts in one timeline-ready package.",
      "Train operations team on liability assumptions versus required response actions.",
      "Audit no-reply inquiry cases and document preventable misses.",
      "Review Stripe authentication flow and disputes docs quarterly for policy updates.",
    ],
    explanationPreviewLines: [
      "This case includes 3DS authentication evidence and inquiry-response timeline controls.",
      "Liability assumptions are documented with supporting transaction artifacts.",
      "Issuer inquiry handling is linked to concrete evidence references, not narrative-only claims.",
      "Authentication and fulfillment records are packaged together for reviewer context.",
      "Workflow includes early escalation for missing 3DS artifacts.",
      "The objective is to avoid no-reply losses where response action was still required.",
    ],
    sourceUrls: [
      "https://docs.stripe.com/payments/3d-secure/authentication-flow",
      "https://docs.stripe.com/disputes/how-disputes-work",
      "https://docs.stripe.com/disputes/responding",
    ],
    faq: [
      {
        question: "Does liability shift mean we can ignore inquiry requests?",
        answer: "No. Stripe documents inquiry paths where response action is still required to avoid escalation or loss.",
      },
      {
        question: "What evidence should be retained for 3DS-related disputes?",
        answer: "Keep authentication, payment, order, and fulfillment artifacts linked in one chronology.",
      },
      {
        question: "Why do no-reply cases still happen with 3DS transactions?",
        answer: "Operational misses happen when teams assume protection and skip timeline-driven response steps.",
      },
      {
        question: "How should this workflow be maintained?",
        answer: "Update SOP with Stripe authentication and disputes documentation reviews on a recurring schedule.",
      },
    ],
  },
  {
    platformSlug: "stripe",
    slug: "dispute-withdrawal-evidence-submission-timing",
    reasonCodeLabel: "Dispute Withdrawal and Submission Timing",
    title: "Stripe Dispute Withdrawal and Evidence Timing Playbook",
    metaDescription:
      "Manage Stripe customer-withdrawn disputes safely by controlling evidence timing, submission decisions, and status verification to avoid false-positive close assumptions.",
    targetSearchQueries: [
      "stripe dispute withdrawn by customer",
      "stripe customer withdraw dispute what to do",
      "stripe submit evidence after withdrawal",
      "stripe dispute status warning_closed",
      "stripe dispute timing after customer contact",
      "stripe dispute workflow withdrawn cases",
    ],
    keyChecks: [
      "Verify dispute status directly in Stripe before assuming withdrawal is final.",
      "Keep evidence package prepared until Stripe status confirms closure path.",
      "Document customer communication that led to potential withdrawal event.",
      "Avoid deleting case artifacts prematurely when status is still open or reviewable.",
      "Coordinate support and payments teams so customer promises match dispute workflow reality.",
      "Use post-withdrawal review to improve scripts that reduce unnecessary escalations.",
    ],
    commonErrors: [
      "Teams stop preparing evidence after customer message but before status confirmation.",
      "Customer says they canceled dispute, yet operator never verifies dashboard status.",
      "Evidence files are removed early and cannot be recovered if dispute remains active.",
      "Support and payments teams maintain separate timelines with contradictory notes.",
      "No process exists for reactivation risk when withdrawal intent is not finalized.",
      "Case is closed internally without final event audit trail.",
    ],
    nextSteps: [
      "Require Stripe status verification checkpoint before any internal close action.",
      "Retain evidence package until final closed status is confirmed in system records.",
      "Store customer communication timestamps with the dispute event timeline.",
      "If status remains actionable, continue readiness workflow and submit on due-by plan.",
      "Run monthly audits on withdrawn-case assumptions that later re-opened.",
      "Standardize closure checklist across support and finance operations.",
    ],
    explanationPreviewLines: [
      "This case includes withdrawal-status verification before closure decisions.",
      "Evidence readiness was maintained until final Stripe status confirmation.",
      "Customer communication and dispute events are linked in one chronology.",
      "Internal close actions were gated by system status, not message-only assumptions.",
      "Artifact retention controls are applied to prevent reactivation blind spots.",
      "The process reduces avoidable losses from premature dispute closure assumptions.",
    ],
    sourceUrls: [
      "https://docs.stripe.com/disputes/withdrawing",
      "https://docs.stripe.com/disputes/how-disputes-work",
      "https://docs.stripe.com/disputes/responding",
    ],
    faq: [
      {
        question: "If a customer says they withdrew, can the case be closed immediately?",
        answer: "Close only after Stripe status confirms the withdrawal path; message-only confirmation is not enough.",
      },
      {
        question: "Should evidence prep continue during withdrawal uncertainty?",
        answer: "Yes. Keep package readiness until final status confirms no further submission action is needed.",
      },
      {
        question: "What is the main operational risk in withdrawn cases?",
        answer: "Premature closure and artifact deletion can leave teams unprepared if the case remains actionable.",
      },
      {
        question: "How can teams reduce this risk long-term?",
        answer: "Use status-verified closure checklists and periodic audits of withdrawal-related outcomes.",
      },
    ],
  },
  {
    platformSlug: "shopify",
    slug: "chargeback-vs-inquiry-operating-playbook",
    reasonCodeLabel: "Chargeback vs Inquiry Operating Playbook",
    title: "Shopify Chargeback vs Inquiry Response Workflow Guide",
    metaDescription:
      "Handle Shopify chargebacks and inquiries with a single operating playbook that clarifies fund movement, response timing, and evidence preparation before final submission.",
    targetSearchQueries: [
      "shopify inquiry vs chargeback",
      "shopify chargeback process timeline",
      "shopify chargeback and inquiry difference",
      "shopify respond to payment inquiry",
      "shopify dispute response workflow",
      "shopify chargeback evidence timeline",
    ],
    keyChecks: [
      "Classify each case as chargeback or inquiry at intake and assign response owner immediately.",
      "Track whether funds are removed or held so finance expectations match dispute status.",
      "Prepare evidence package early even for inquiries to reduce escalation shock.",
      "Monitor Shopify admin case updates daily until final decision.",
      "Keep reason-specific evidence map tied to order, fulfillment, and policy records.",
      "Align support messaging with dispute workflow to avoid contradictory customer communications.",
    ],
    commonErrors: [
      "Operations treats inquiry as low urgency and loses response time when escalation follows.",
      "Finance team expects automatic fund return before final network decision.",
      "Evidence prep starts only after status worsens, creating rushed uploads.",
      "Case ownership changes without timeline handoff and key facts are lost.",
      "Customer support promises outcomes that conflict with card-network dispute process.",
      "Admin updates are reviewed sporadically, delaying required actions.",
    ],
    nextSteps: [
      "Build intake triage: inquiry, chargeback, unknown with SLA by case type.",
      "Create daily case board with status, due date, owner, and readiness score.",
      "Standardize evidence chronology template used for both inquiry and chargeback paths.",
      "Review unresolved inquiries twice weekly for early escalation indicators.",
      "Integrate finance notes so fund-impact expectations are documented per case.",
      "Run monthly postmortem on cases that escalated due to response delay.",
    ],
    explanationPreviewLines: [
      "This case is managed under inquiry-vs-chargeback workflow with named ownership.",
      "Fund-impact status and response timeline are tracked alongside evidence readiness.",
      "Evidence chronology was prepared before escalation risk matured.",
      "Submission artifacts are mapped to order, fulfillment, and communication evidence.",
      "Support and payments actions are synchronized to avoid contradictory handling.",
      "The playbook is designed to reduce avoidable escalation and timeline misses.",
    ],
    sourceUrls: [
      "https://help.shopify.com/en/manual/payments/chargebacks/chargeback-process",
      "https://help.shopify.com/en/manual/payments/chargebacks/resolve-chargeback",
    ],
    faq: [
      {
        question: "Is an inquiry the same as a chargeback in Shopify?",
        answer: "No. They are different dispute stages and can have different urgency and fund-impact behavior.",
      },
      {
        question: "Should inquiry cases have full evidence prep?",
        answer: "Yes, early prep reduces risk if the case escalates into a full chargeback path.",
      },
      {
        question: "How often should case status be checked?",
        answer: "Daily checks are recommended for active dispute queues to avoid timing misses.",
      },
      {
        question: "Why align support and payments teams on one workflow?",
        answer: "Misaligned communication creates operational confusion and weakens dispute handling consistency.",
      },
    ],
  },
  {
    platformSlug: "shopify",
    slug: "chargeback-response-status-open-submitted-won-lost",
    reasonCodeLabel: "Response Status Open Submitted Won Lost",
    title: "Shopify Chargeback Status Open Submitted Won Lost Guide",
    metaDescription:
      "Operate Shopify chargeback queues with status-driven checklists so open, submitted, won, and lost stages each trigger the right evidence and follow-up actions.",
    targetSearchQueries: [
      "shopify chargeback status open submitted won lost",
      "shopify chargeback status meanings",
      "shopify dispute submitted what next",
      "shopify won chargeback payout timing",
      "shopify lost chargeback next steps",
      "shopify chargeback admin status workflow",
    ],
    keyChecks: [
      "Define required operator action for each status: open, submitted, won, lost.",
      "Lock evidence package after submitted status unless platform guidance requires update.",
      "Record decision outcomes and payout effects per case for financial reconciliation.",
      "Use status-based templates to standardize internal communication and stakeholder updates.",
      "Track repeated lost-status reasons to identify policy, fraud, or fulfillment weaknesses.",
      "Archive final artifacts and timeline once case enters terminal status.",
    ],
    commonErrors: [
      "Teams submit evidence but fail to monitor post-submit status transitions.",
      "Won cases are not reconciled against expected fund return timing.",
      "Lost cases close without root-cause analysis, repeating preventable patterns.",
      "No single status dictionary exists, so operators interpret states inconsistently.",
      "Internal reports mix inquiry and chargeback terminal outcomes incorrectly.",
      "Evidence archives are incomplete when finance or audit requests arrive later.",
    ],
    nextSteps: [
      "Publish status playbook with owner, SLA, and required artifacts for each state.",
      "Automate status-change notifications to operations and finance channels.",
      "Run weekly review of submitted-but-stale cases that need follow-up checks.",
      "Create won/lost outcome dashboard by reason code and fulfillment profile.",
      "Use lost-case lessons to update prevention and checkout controls.",
      "Enforce final archive checklist before case closure in internal systems.",
    ],
    explanationPreviewLines: [
      "This case follows status-based handling rules from open through final outcome.",
      "Evidence package integrity is preserved after submission to keep audit consistency.",
      "Outcome and fund-impact details are captured for reconciliation and learning.",
      "Reason-level trend capture supports prevention updates after terminal decisions.",
      "All artifacts are archived with timeline traceability at case close.",
      "Status discipline is used to reduce avoidable process drift in dispute operations.",
    ],
    sourceUrls: [
      "https://help.shopify.com/en/manual/payments/chargebacks/chargebacks-in-admin",
      "https://help.shopify.com/en/manual/payments/chargebacks/resolve-chargeback",
    ],
    faq: [
      {
        question: "Why do status-specific checklists matter?",
        answer: "Each status requires different actions, and missing those actions causes operational gaps or reconciliation errors.",
      },
      {
        question: "Should submitted cases still be monitored?",
        answer: "Yes. Post-submit monitoring is needed to capture final outcomes and follow-up requirements.",
      },
      {
        question: "What should happen after a lost case?",
        answer: "Run root-cause analysis and feed findings into prevention and evidence quality playbooks.",
      },
      {
        question: "What should be archived at closure?",
        answer: "Store final status, timeline, submitted artifacts, and fund-impact notes for audit and learning.",
      },
    ],
  },
  {
    platformSlug: "shopify",
    slug: "chargeback-monitoring-programs-vamp-match-risk",
    reasonCodeLabel: "Monitoring Programs and VAMP MATCH Risk",
    title: "Shopify Monitoring Programs VAMP MATCH Risk Guide",
    metaDescription:
      "Reduce Shopify monitoring-program pressure by tracking dispute patterns, preparing account-level mitigation plans, and aligning prevention and response operations.",
    targetSearchQueries: [
      "shopify chargeback monitoring programs",
      "shopify vamp match risk",
      "shopify excessive chargeback rate",
      "shopify dispute monitoring threshold",
      "shopify risk of being placed in match",
      "shopify reduce chargeback ratio",
    ],
    keyChecks: [
      "Monitor dispute trends and program-related alerts from Shopify and payment partners.",
      "Segment dispute inflow by reason, channel, and region to identify concentration risk.",
      "Create mitigation runbook for spikes before account restrictions escalate.",
      "Coordinate fraud prevention controls with dispute response playbooks.",
      "Document all remediation actions and verify impact on subsequent dispute periods.",
      "Review monitoring guidance updates regularly because program frameworks can change.",
    ],
    commonErrors: [
      "Teams notice program risk only after receiving severe warnings or restrictions.",
      "Dispute ratios are tracked in aggregate without reason-level diagnosis.",
      "Mitigation actions are undocumented, so effectiveness cannot be measured.",
      "Fraud prevention and chargeback response teams work in isolation.",
      "Operations ignores country or network mix effects in monitoring exposure.",
      "Program guidance changes are missed due to infrequent policy review.",
    ],
    nextSteps: [
      "Stand up weekly monitoring review with risk, support, and payments stakeholders.",
      "Create top-cause heatmap by reason code and merchant segment.",
      "Deploy mitigation experiments with explicit success metrics and review dates.",
      "Tighten checkout and fulfillment controls for high-risk segments first.",
      "Report remediation progress to leadership with consistent monthly baseline.",
      "Update SOP immediately when Shopify monitoring guidance is revised.",
    ],
    explanationPreviewLines: [
      "This case is tracked under monitoring-program risk workflow with segmented metrics.",
      "Reason-level analysis informs both prevention and response improvements.",
      "Mitigation actions are documented with measurable impact checkpoints.",
      "Cross-functional governance is used to reduce program escalation risk.",
      "Policy updates are incorporated into operations through scheduled review cycles.",
      "The objective is sustained ratio control, not one-off tactical fixes.",
    ],
    sourceUrls: [
      "https://help.shopify.com/en/manual/payments/chargebacks/monitoring-programs",
      "https://help.shopify.com/en/manual/payments/chargebacks/chargeback-process",
    ],
    faq: [
      {
        question: "What is the biggest mistake in monitoring-program readiness?",
        answer: "Waiting for severe alerts before taking action usually leaves too little runway for effective mitigation.",
      },
      {
        question: "Which metric should be reviewed first?",
        answer: "Start with dispute-rate trend by reason and channel, then drill into operational root causes.",
      },
      {
        question: "Can response quality alone solve monitoring risk?",
        answer: "No. Prevention and response must be improved together to reduce both inflow and outcome pressure.",
      },
      {
        question: "How often should guidance be reviewed?",
        answer: "Review policy and program guidance on a recurring cadence because frameworks may change over time.",
      },
    ],
  },
  {
    platformSlug: "shopify",
    slug: "chargeback-fee-recovery-and-country-rules",
    reasonCodeLabel: "Chargeback Fee Recovery and Country Rules",
    title: "Shopify Chargeback Fee Recovery and Country Rules Guide",
    metaDescription:
      "Use country-aware fee and payout logic for Shopify chargebacks so finance and operations can decide contest strategy with clear recovery and cost assumptions.",
    targetSearchQueries: [
      "shopify chargeback fee",
      "shopify chargeback fee returned if win",
      "shopify chargeback fees by country",
      "shopify should i contest chargeback",
      "shopify dispute payout after win",
      "shopify chargeback cost planning",
    ],
    keyChecks: [
      "Confirm chargeback fee and recovery behavior for your operating country and payment setup.",
      "Tie contest decisions to case value, evidence strength, and fee exposure.",
      "Capture expected payout timing for won disputes in finance planning.",
      "Avoid contesting low-value cases without clear expected recovery advantage.",
      "Reconcile final outcomes against projected economics for continuous calibration.",
      "Keep policy references current and avoid relying on community anecdotes for fee terms.",
    ],
    commonErrors: [
      "Finance assumes a single fee policy globally despite country-specific differences.",
      "Teams contest every dispute without economic threshold or evidence readiness check.",
      "Won-case fund return timing is not tracked, creating reconciliation confusion.",
      "Chargeback fee assumptions are copied from old playbooks and not revalidated.",
      "Case economics are not reviewed by reason code, masking low-ROI segments.",
      "Operational and finance systems track outcomes differently and cannot be reconciled.",
    ],
    nextSteps: [
      "Build country-aware fee matrix for all active markets in your store footprint.",
      "Apply counter/no-counter threshold by case value and evidence confidence.",
      "Add payout-reconciliation tasks to won-case closure workflow.",
      "Review quarterly whether fee policy or country rules have changed.",
      "Publish one-page decision rubric for operations and finance alignment.",
      "Run monthly variance analysis between projected and actual dispute economics.",
    ],
    explanationPreviewLines: [
      "This case was evaluated with country-aware chargeback fee assumptions.",
      "Contest decision was tied to evidence readiness and expected recovery economics.",
      "Fund-impact notes are captured for post-decision reconciliation.",
      "Outcome data is logged to improve future threshold calibration.",
      "Policy references are taken from current official Shopify documentation.",
      "The workflow is designed to avoid fee-negative dispute handling behavior.",
    ],
    sourceUrls: [
      "https://help.shopify.com/en/manual/payments/chargebacks/chargeback-process",
      "https://help.shopify.com/en/manual/payments/chargebacks/chargebacks-in-admin",
    ],
    faq: [
      {
        question: "Are Shopify chargeback fees identical in every country?",
        answer: "No. Shopify documentation indicates country-specific fee behavior, so local policy checks are required.",
      },
      {
        question: "Should every chargeback be contested?",
        answer: "Use evidence and economics together. Some low-value cases may not justify manual effort and fee risk.",
      },
      {
        question: "How do we avoid reconciliation gaps after won cases?",
        answer: "Track expected versus actual fund movement and close cases only after reconciliation is complete.",
      },
      {
        question: "How often should fee assumptions be reviewed?",
        answer: "Review periodically and after policy updates to keep operational decisions current.",
      },
    ],
  },
  {
    platformSlug: "shopify",
    slug: "shopify-protect-fraud-unrecognized-coverage",
    reasonCodeLabel: "Shopify Protect Fraud Coverage",
    title: "Shopify Protect Coverage for Fraudulent Chargebacks Guide",
    metaDescription:
      "Understand Shopify Protect eligibility and workflow for fraudulent or unrecognized claims so covered orders are handled correctly without evidence-handling confusion.",
    targetSearchQueries: [
      "shopify protect chargeback coverage",
      "shopify protect fraudulent unrecognized chargeback",
      "shopify protect eligibility order",
      "shopify protect does it cover all chargebacks",
      "shopify protect dispute workflow",
      "shopify protect claim handling",
    ],
    keyChecks: [
      "Confirm whether the order is marked as protected before choosing manual response path.",
      "Verify that dispute reason aligns with documented Shopify Protect coverage scope.",
      "Retain order and fulfillment records even when coverage is expected.",
      "Ensure internal teams understand covered versus non-covered reason categories.",
      "Track protected-order outcomes separately from manual dispute cases.",
      "Document exceptions where expected coverage did not apply and why.",
    ],
    commonErrors: [
      "Teams assume Shopify Protect covers all dispute reasons and skip reason verification.",
      "Protected-order markers are not checked before escalating to manual response work.",
      "Evidence hygiene is ignored because coverage is assumed guaranteed.",
      "Support staff cannot explain why one order was protected and another was not.",
      "Protected and non-protected cases are mixed in reporting, hiding true performance.",
      "Eligibility assumptions are not revisited after policy or product updates.",
    ],
    nextSteps: [
      "Add eligibility check field to case intake and block manual path until checked.",
      "Create reason-code map showing covered and non-covered categories for operators.",
      "Maintain evidence-quality baseline even for protected orders.",
      "Separate KPI reporting for protected and manual dispute cohorts.",
      "Audit exceptions monthly and update team guidance with real examples.",
      "Review Shopify Protect documentation whenever product policy updates are announced.",
    ],
    explanationPreviewLines: [
      "Order eligibility and reason scope were checked against Shopify Protect guidance.",
      "Protected-versus-manual handling path is documented before submission actions.",
      "Evidence records are preserved to support traceability even when coverage is expected.",
      "Case reporting tags distinguish protected outcomes from manual dispute outcomes.",
      "Team guidance includes exception handling for non-covered scenarios.",
      "The process reduces confusion between policy coverage and operational readiness.",
    ],
    sourceUrls: [
      "https://help.shopify.com/en/manual/payments/shopify-protect/protect-order-with-shopify-protect",
      "https://help.shopify.com/en/manual/payments/chargebacks/resolve-chargeback",
      "https://help.shopify.com/en/manual/payments/chargebacks/chargeback-reasons",
    ],
    faq: [
      {
        question: "Does Shopify Protect cover every chargeback reason?",
        answer: "No. Coverage depends on eligibility and reason scope, so each case should be verified against current policy.",
      },
      {
        question: "Do protected orders still need evidence records?",
        answer: "Yes. Keep records for traceability, support handoffs, and exception handling.",
      },
      {
        question: "Why separate reporting for protected cases?",
        answer: "It prevents blended metrics from hiding whether manual workflow or coverage policy drives outcomes.",
      },
      {
        question: "How should teams stay current on coverage rules?",
        answer: "Review official Shopify Protect documentation on a recurring basis and update SOP accordingly.",
      },
    ],
  },
  {
    platformSlug: "shopify",
    slug: "chargeback-prevention-billing-descriptor-avs-tracking",
    reasonCodeLabel: "Prevention Descriptor AVS Tracking Checklist",
    title: "Shopify Chargeback Prevention Descriptor AVS Tracking Guide",
    metaDescription:
      "Lower Shopify dispute inflow with practical prevention controls including clear billing descriptors, AVS/CVV checks, and fulfillment tracking evidence standards.",
    targetSearchQueries: [
      "shopify chargeback prevention checklist",
      "shopify billing descriptor chargeback",
      "shopify avs cvv dispute prevention",
      "shopify tracking info chargeback prevention",
      "reduce shopify chargebacks operations",
      "shopify prevent fraudulent chargebacks",
    ],
    keyChecks: [
      "Use a recognizable billing descriptor so customers can identify transactions clearly.",
      "Apply AVS/CVV and risk checks according to fraud profile and false-positive tolerance.",
      "Require trackable fulfillment evidence and delivery confirmation for risky order segments.",
      "Set policy communication points at checkout and post-purchase to reduce expectation disputes.",
      "Keep customer-support response SLAs tight to defuse avoidable dispute escalation.",
      "Link prevention controls to dispute outcomes so high-impact controls can be prioritized.",
    ],
    commonErrors: [
      "Descriptor is unclear, leading customers to file disputes for unrecognized charges.",
      "AVS/CVV signals are collected but not used consistently in fulfillment decisions.",
      "Tracking evidence is incomplete or stored in tools disconnected from dispute operations.",
      "Support backlog delays simple refunds that could prevent formal chargebacks.",
      "Checkout policy text is vague and does not match post-purchase communication.",
      "Prevention controls are added without measuring impact on dispute inflow.",
    ],
    nextSteps: [
      "Audit billing descriptor clarity and customer support scripts in top dispute segments.",
      "Define AVS/CVV decision policy by risk tier and review false-positive outcomes.",
      "Standardize tracking and proof-of-delivery retention for all high-risk orders.",
      "Set pre-dispute customer resolution SLA and monitor compliance weekly.",
      "Run monthly prevention KPI review by reason code and channel.",
      "Iterate controls based on measured inflow reduction and customer-experience impact.",
    ],
    explanationPreviewLines: [
      "This dispute-prevention playbook uses descriptor, AVS/CVV, and tracking controls together.",
      "Customer recognition and communication controls are aligned to reduce avoidable disputes.",
      "Fulfillment evidence retention supports faster response when disputes still occur.",
      "Support-resolution SLA is part of prevention, not only post-dispute handling.",
      "Control impact is measured by reason and channel for iterative improvement.",
      "The objective is sustained inflow reduction with operational accountability.",
    ],
    sourceUrls: [
      "https://help.shopify.com/en/manual/payments/chargebacks/preventing-chargebacks",
      "https://help.shopify.com/en/manual/payments/chargebacks/chargeback-process",
      "https://help.shopify.com/en/manual/payments/chargebacks/chargeback-reasons",
    ],
    faq: [
      {
        question: "Why does billing descriptor clarity matter so much?",
        answer: "Unrecognized descriptors are a common trigger for customer-initiated disputes that could be prevented.",
      },
      {
        question: "Are AVS/CVV checks enough on their own?",
        answer: "No. They should be paired with fulfillment evidence and customer communication controls.",
      },
      {
        question: "How does support response speed affect chargebacks?",
        answer: "Slow support increases escalation risk; fast resolution can prevent some disputes from formalizing.",
      },
      {
        question: "What is the best way to prioritize prevention controls?",
        answer: "Use measured dispute inflow impact by reason and channel, then focus on the highest-leverage gaps.",
      },
    ],
  },
];

function validate(guides) {
  if (guides.length < MIN_GUIDE_FLOOR) {
    throw new Error(`expected at least ${MIN_GUIDE_FLOOR} guides, got ${guides.length}`);
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
  for (const spec of EXPANSION_REASON_SPECS) guides.push(topicReasonEntry(spec));

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
