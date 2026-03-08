import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { PDFDocument, StandardFonts, rgb, degrees } from "pdf-lib";
import { Jimp, JimpMime, loadFont, measureTextHeight } from "jimp";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "..");
const outputRoot = path.join(repoRoot, "output", "pdf", "synthetic-evidence-sets");
const fontRoot = path.join(repoRoot, "node_modules", "@jimp", "plugin-print", "fonts", "open-sans");

const fonts = await loadFonts();

const baseScenarios = {
  northwindInr: {
    platform: "Stripe",
    scope: "STRIPE_DISPUTE",
    reason: "PRODUCT_NOT_RECEIVED",
    merchant: "Northwind Pet Supply",
    customer: "Avery Morgan",
    email: "avery.morgan@example-demo.com",
    phone: "+1 415 555 0149",
    amount: "$82.40",
    orderId: "NW-48219",
    transactionId: "pi_demo_48219",
    orderDate: "2026-02-09 14:12 UTC",
    address: "880 Market Street, Apt 8, San Francisco, CA 94102",
    itemLines: ["Travel carrier - $49.00", "Harness insert - $19.00", "Shipping - $14.40"],
    communicationBullets: [
      "Customer asked where the parcel was and requested updated tracking.",
      "Support sent the carrier link and ETA.",
      "Merchant escalated a carrier trace after follow-up.",
    ],
    fulfillmentBullets: [
      "Carrier accepted parcel on 2026-02-10.",
      "Final scan marked delivered to front desk on 2026-02-13.",
      "Carrier GPS delivery radius matched destination building.",
    ],
    policyParagraphs: [
      "Orders are treated as fulfilled when the carrier marks the parcel delivered.",
      "Non-delivery reports trigger carrier investigation and merchant follow-up.",
    ],
    timelineBullets: [
      "2026-02-09 - Order placed and payment captured.",
      "2026-02-10 - Shipment packed and handed to carrier.",
      "2026-02-13 - Carrier marked parcel delivered.",
      "2026-02-16 - Customer reported non-delivery to support.",
    ],
  },
  signalFraud: {
    platform: "Stripe",
    scope: "STRIPE_DISPUTE",
    reason: "FRAUDULENT",
    merchant: "Signal Forge Labs",
    customer: "Jordan Kim",
    email: "jordan.kim@example-demo.com",
    phone: "+1 626 555 0171",
    amount: "$149.00",
    orderId: "SFL-20815",
    transactionId: "pi_demo_20815",
    orderDate: "2026-01-29 20:08 UTC",
    address: "1147 Mission Avenue, Suite 400, Pasadena, CA 91101",
    itemLines: ["Pro annual license - $149.00"],
    communicationBullets: [
      "Receipt email delivered immediately after payment.",
      "Welcome email included license key and download link.",
      "Customer later asked where to download the desktop app.",
    ],
    digitalBullets: [
      "License activated 4 minutes after payment succeeded.",
      "Three authenticated sign-ins recorded over four days.",
      "Eleven project exports and two API token creations logged.",
    ],
    policyParagraphs: [
      "Digital access is delivered immediately after payment succeeds.",
      "Usage logs and license activations are retained for fraud review.",
    ],
  },
  harborCredit: {
    platform: "Stripe",
    scope: "STRIPE_DISPUTE",
    reason: "CREDIT_NOT_PROCESSED",
    merchant: "Harbor Trail Outfitters",
    customer: "Leah Patel",
    email: "leah.patel@example-demo.com",
    phone: "+1 206 555 0126",
    amount: "$64.90",
    orderId: "HT-19007",
    transactionId: "pi_demo_19007",
    orderDate: "2026-01-11 13:08 UTC",
    address: "4021 Meridian Ave N, Seattle, WA 98103",
    itemLines: ["Hydration vest - $52.00", "Shipping - $12.90"],
    communicationBullets: [
      "Customer requested refund after reporting fit issues.",
      "Support sent return instructions and refund timing expectations.",
      "Customer confirmed parcel drop-off with tracking reference.",
    ],
    refundParagraphs: [
      "Gateway log indicates refund creation succeeded on 2026-01-17.",
      "Refund confirmation email was sent automatically after the credit was created.",
    ],
    policyParagraphs: [
      "Refunds are issued to the original payment method after return intake.",
      "Bank posting times vary by issuer after a refund is transmitted.",
    ],
  },
  mapleInr: {
    platform: "Shopify",
    scope: "SHOPIFY_PAYMENTS_CHARGEBACK",
    reason: "PRODUCT_NOT_RECEIVED",
    merchant: "Maple Home Studio",
    customer: "Noah Rivera",
    email: "noah.rivera@example-demo.com",
    phone: "+1 312 555 0192",
    amount: "$118.00",
    orderId: "MHS-4107",
    transactionId: "shop_demo_4107",
    orderDate: "2026-02-03 18:07 UTC",
    address: "2516 West Roscoe Street, Chicago, IL 60618",
    itemLines: ["2 x ceramic pendant shades - $96.00", "Shipping - $22.00"],
    communicationBullets: [
      "Customer reported no package arrival and requested status update.",
      "Merchant replied with Shopify fulfillment status and carrier link.",
      "Merchant opened a carrier claim and promised follow-up.",
    ],
    fulfillmentBullets: [
      "Fulfillment created on 2026-02-04 07:50 UTC.",
      "Carrier marked order delivered on 2026-02-07.",
      "Final scan reported porch delivery with no exception.",
    ],
    policyParagraphs: [
      "Customers receive tracking details when Shopify marks the order fulfilled.",
      "Non-delivery claims trigger carrier investigation and merchant follow-up.",
    ],
  },
  softHarborUnacceptable: {
    platform: "Shopify",
    scope: "SHOPIFY_PAYMENTS_CHARGEBACK",
    reason: "PRODUCT_UNACCEPTABLE",
    merchant: "Soft Harbor Living",
    customer: "Mia Thompson",
    email: "mia.thompson@example-demo.com",
    phone: "+1 202 555 0107",
    amount: "$73.50",
    orderId: "SHL-5902",
    transactionId: "shop_demo_5902",
    orderDate: "2026-01-27 21:16 UTC",
    address: "1178 Columbia Road NW, Washington, DC 20009",
    itemLines: ["Linen duvet cover - $59.00", "Shipping - $14.50"],
    communicationBullets: [
      "Customer reported color mismatch compared with listing photos.",
      "Merchant offered exchange or return after photo review.",
      "Customer declined exchange and asked for refund options.",
    ],
    fulfillmentBullets: [
      "Package delivered on 2026-01-31 with no carrier exception.",
      "Package weight matched the duvet cover SKU profile.",
    ],
    refundParagraphs: [
      "Merchant approved return review and sent a prepaid return label.",
      "Refund had not yet been issued because the return parcel had not been scanned at intake.",
    ],
    policyParagraphs: [
      "Customers may return products that differ materially from the description within 30 days.",
      "Merchant offers replacement, store credit, or refund after returned goods are received.",
    ],
  },
};

const scenarios = [
  {
    id: "stripe_product_not_received",
    title: "Stripe product not received",
    messyInputs: "Mostly clean exported PDFs.",
    whyServiceMatters: "Baseline happy path for a merchant who already exported clean files.",
    docs: ["order_receipt_pdf", "customer_profile_pdf", "communication_pdf", "fulfillment_pdf", "policy_pdf", "timeline_pdf"],
    ...baseScenarios.northwindInr,
  },
  {
    id: "stripe_fraudulent_digital_access",
    title: "Stripe fraudulent digital access",
    messyInputs: "Mostly clean exported PDFs.",
    whyServiceMatters: "Baseline digital-goods example with usage logs already exported.",
    docs: ["order_receipt_pdf", "customer_profile_pdf", "digital_logs_pdf", "communication_pdf", "policy_pdf"],
    ...baseScenarios.signalFraud,
  },
  {
    id: "stripe_credit_not_processed",
    title: "Stripe credit not processed",
    messyInputs: "Mostly clean exported PDFs.",
    whyServiceMatters: "Baseline refund dispute example with gateway logs already collected.",
    docs: ["order_receipt_pdf", "communication_pdf", "refund_log_pdf", "customer_profile_pdf", "policy_pdf"],
    ...baseScenarios.harborCredit,
  },
  {
    id: "shopify_product_not_received",
    title: "Shopify product not received",
    messyInputs: "Mostly clean exported PDFs.",
    whyServiceMatters: "Baseline Shopify pack for comparison against messier cases.",
    docs: ["order_receipt_pdf", "communication_pdf", "fulfillment_pdf", "customer_profile_pdf", "policy_pdf"],
    ...baseScenarios.mapleInr,
  },
  {
    id: "shopify_product_unacceptable",
    title: "Shopify product unacceptable",
    messyInputs: "Mostly clean exported PDFs.",
    whyServiceMatters: "Baseline Shopify quality-issue case with organized documents.",
    docs: ["order_receipt_pdf", "communication_pdf", "policy_pdf", "fulfillment_pdf", "refund_log_pdf"],
    ...baseScenarios.softHarborUnacceptable,
  },
  {
    id: "edge_missing_required_minimal",
    title: "Edge case missing required evidence",
    messyInputs: "One phone screenshot and one extra PDF, but still missing core evidence.",
    whyServiceMatters: "Shows the exact gap between format-pass and export-ready with a realistic minimal input.",
    docs: ["receipt_email_png", "timeline_pdf"],
    orderId: "NW-EDGE-1001",
    transactionId: "pi_demo_edge_1001",
    timelineBullets: [
      "Upload this pack to confirm format checks can pass before the case is export-ready.",
      "The missing customer profile and delivery proof should still block export readiness.",
    ],
    ...baseScenarios.northwindInr,
  },
  {
    id: "edge_duplicate_order_receipt",
    title: "Edge case duplicate order receipt",
    messyInputs: "Receipt email screenshot plus a second receipt photo for the same order.",
    whyServiceMatters: "Useful for testing duplicate-type merge messaging when merchants upload redundant proof.",
    docs: ["receipt_email_png", "receipt_gateway_photo_jpg", "customer_profile_png", "chat_screenshot_png", "tracking_screenshot_png"],
    orderId: "NW-EDGE-2002",
    transactionId: "pi_demo_edge_2002",
    ...baseScenarios.northwindInr,
  },
  {
    id: "edge_manual_mapping_needed",
    title: "Edge case manual evidence mapping",
    messyInputs: "Generic filenames from phone gallery export with mixed PNG and JPEG files.",
    whyServiceMatters: "Tests whether the mapping modal is understandable when filenames are useless.",
    docs: ["generic_receipt_capture", "generic_customer_capture", "generic_chat_capture"],
    orderId: "NW-EDGE-3003",
    transactionId: "pi_demo_edge_3003",
    ...baseScenarios.northwindInr,
  },
  {
    id: "stripe_inr_phone_gallery_mix",
    title: "Stripe INR phone gallery mix",
    messyInputs: "Phone screenshots, one camera photo, mixed extensions, and user-style filenames.",
    whyServiceMatters: "Closer to the real-world merchant case where evidence is scattered across screenshots and camera captures.",
    docs: ["receipt_email_png", "customer_profile_png", "chat_screenshot_png", "delivery_photo_jpg", "policy_screenshot_png", "timeline_pdf"],
    orderId: "NW-MIX-4104",
    transactionId: "pi_demo_mix_4104",
    ...baseScenarios.northwindInr,
  },
  {
    id: "shopify_oversized_phone_photos",
    title: "Shopify oversized phone photos",
    messyInputs: "Oversized PNG/JPEG captures straight from a phone camera and screenshot roll.",
    whyServiceMatters: "Designed for Shopify image compression and size-budget edge cases.",
    docs: ["oversized_receipt_png", "oversized_delivery_photo_jpg", "customer_profile_png", "chat_screenshot_png", "policy_photo_jpg"],
    orderId: "MHS-EDGE-5501",
    transactionId: "shop_demo_edge_5501",
    ...baseScenarios.mapleInr,
  },
  {
    id: "edge_split_chat_screenshots",
    title: "Edge case split chat screenshots",
    messyInputs: "Three separate support screenshots for one conversation plus other mixed files.",
    whyServiceMatters: "Shows why merge and classification help when chat evidence arrives as fragments.",
    docs: ["receipt_email_png", "customer_profile_png", "tracking_screenshot_png", "chat_part_1_png", "chat_part_2_png", "chat_part_3_png"],
    orderId: "NW-EDGE-6106",
    transactionId: "pi_demo_edge_6106",
    ...baseScenarios.northwindInr,
  },
  {
    id: "edge_camera_scan_credit_bundle",
    title: "Edge case camera scan credit bundle",
    messyInputs: "Camera photos, screenshots, and scan-like JPEGs from a refund workflow.",
    whyServiceMatters: "Useful for refund disputes where merchants have only phone photos of paperwork and inbox screenshots.",
    docs: ["camera_receipt_photo_jpg", "refund_email_png", "return_label_photo_jpg", "policy_scan_photo_jpg", "customer_profile_png"],
    orderId: "HT-EDGE-7208",
    transactionId: "pi_demo_edge_7208",
    ...baseScenarios.harborCredit,
  },
  {
    id: "stripe_fraud_digital_logs_mix",
    title: "Stripe fraud digital logs mix",
    messyInputs: "Phone screenshots, one admin session-log capture, and mixed merchant-facing proof.",
    whyServiceMatters: "Expands testing beyond INR so digital-usage evidence is exercised on a reason-specific Stripe flow.",
    docs: ["receipt_email_png", "customer_profile_png", "digital_session_screenshot_png", "chat_screenshot_png", "policy_screenshot_png"],
    orderId: "SFL-MIX-8101",
    transactionId: "pi_demo_mix_8101",
    ...baseScenarios.signalFraud,
  },
  {
    id: "shopify_unacceptable_policy_refund_mix",
    title: "Shopify unacceptable policy refund mix",
    messyInputs: "Messy screenshots, help-center capture, refund email, and one delivery photo in the same pack.",
    whyServiceMatters: "Covers Shopify product-unacceptable disputes where merchants need policy, communication, and refund context together.",
    docs: ["receipt_email_png", "chat_screenshot_png", "policy_screenshot_png", "refund_email_png", "delivery_photo_jpg"],
    orderId: "SHL-MIX-8202",
    transactionId: "shop_demo_mix_8202",
    ...baseScenarios.softHarborUnacceptable,
  },
  {
    id: "edge_bad_ocr_manual_mapping",
    title: "Edge case bad OCR manual mapping",
    messyInputs: "Generic IMG filenames, blur, low contrast, and screenshot captures that should weaken OCR confidence.",
    whyServiceMatters: "Tests whether the mapping flow still feels usable when both filenames and OCR are weak.",
    docs: ["bad_ocr_receipt_photo_jpg", "bad_ocr_chat_capture_png", "bad_ocr_policy_capture_png"],
    orderId: "SHL-EDGE-8303",
    transactionId: "shop_demo_edge_8303",
    ...baseScenarios.softHarborUnacceptable,
  },
];

const artifactTemplates = {
  order_receipt_pdf: (s) => pdfArtifact("order_receipt_invoice.pdf", "ORDER_RECEIPT", "Order receipt and invoice", "Synthetic demo document", [
    ["Merchant", s.merchant],
    ["Order ID", s.orderId],
    ["Transaction ID", s.transactionId],
    ["Customer", s.customer],
    ["Order date", s.orderDate],
    ["Amount", s.amount],
  ], [{ heading: "Items", bullets: s.itemLines }]),
  customer_profile_pdf: (s) => pdfArtifact("customer_profile.pdf", "CUSTOMER_DETAILS", "Customer details and billing profile", "Synthetic demo document", [
    ["Customer", s.customer],
    ["Email", s.email],
    ["Phone", s.phone],
    ["Address", s.address],
  ], [{
    heading: "Profile notes",
    bullets: [
      "Customer identity is fictional and for testing only.",
      "Record is formatted to mimic merchant support data.",
    ],
  }]),
  communication_pdf: (s) => pdfArtifact("customer_communication.pdf", "CUSTOMER_COMMUNICATION", "Customer communication thread", "Synthetic demo document", [
    ["Channel", "Email / support inbox"],
    ["Customer", s.customer],
    ["Reference", `${s.orderId}-COMM`],
  ], [{ heading: "Message excerpts", bullets: s.communicationBullets }]),
  fulfillment_pdf: (s) => pdfArtifact("fulfillment_delivery.pdf", "FULFILLMENT_DELIVERY", "Fulfillment and delivery summary", "Synthetic demo document", [
    ["Reference", `${s.orderId}-SHIP`],
    ["Destination", s.address],
    ["Platform", s.platform],
  ], [{ heading: "Fulfillment notes", bullets: s.fulfillmentBullets }]),
  policy_pdf: (s) => pdfArtifact("policy_excerpt.pdf", "POLICIES", "Policy excerpt", "Synthetic demo document", [
    ["Merchant", s.merchant],
    ["Scope", s.reason],
  ], [{ heading: "Policy summary", paragraphs: s.policyParagraphs }]),
  refund_log_pdf: (s) => pdfArtifact("refund_or_cancellation_log.pdf", "REFUND_CANCELLATION", "Refund or cancellation log", "Synthetic demo document", [
    ["Reference", `${s.orderId}-RFND`],
    ["Customer", s.customer],
    ["Amount", s.amount],
  ], [{ heading: "Status notes", paragraphs: s.refundParagraphs }]),
  digital_logs_pdf: (s) => pdfArtifact("digital_usage_logs.pdf", "DIGITAL_USAGE_LOGS", "Digital usage and access logs", "Synthetic demo document", [
    ["Reference", `${s.orderId}-DIGI`],
    ["Customer", s.customer],
    ["Amount", s.amount],
  ], [{ heading: "Usage highlights", bullets: s.digitalBullets }]),
  digital_session_screenshot_png: (s) => phoneArtifact("device_activity_capture.png", "DIGITAL_USAGE_LOGS", "Session activity capture", "Admin screenshot", {
    appName: "Risk Console",
    headerTag: "Sessions",
    cards: [
      {
        heading: "Session overview",
        rows: [
          ["Customer", s.customer],
          ["Reference", `${s.orderId}-DIGI`],
          ["Primary device", "Desktop Chrome on macOS"],
        ],
      },
      {
        heading: "Recent activity",
        bullets: s.digitalBullets,
      },
      {
        heading: "Signals",
        paragraphs: ["IP address 203.0.113.42", "Session token refreshed after payment succeeded."],
      },
    ],
  }),
  timeline_pdf: (s) => pdfArtifact("timeline_summary.pdf", "OTHER_SUPPORTING", "Timeline summary", "Synthetic demo document", [
    ["Case", s.title],
    ["Order ID", s.orderId],
    ["Reason", s.reason],
  ], [{ heading: "Timeline", bullets: s.timelineBullets }]),
  receipt_email_png: (s) => phoneArtifact("Screenshot_2026-02-09_1419.png", "ORDER_RECEIPT", "Receipt email", "Phone screenshot capture", {
    appName: "Mail",
    headerTag: "Inbox",
    cards: [
      {
        heading: "Payment summary",
        rows: [
          ["Merchant", s.merchant],
          ["Order ID", s.orderId],
          ["Transaction ID", s.transactionId],
          ["Amount", s.amount],
        ],
      },
      {
        heading: "Line items",
        bullets: s.itemLines,
      },
    ],
  }),
  customer_profile_png: (s) => phoneArtifact("billing_profile_capture.png", "CUSTOMER_DETAILS", "Customer profile capture", "Admin or checkout screenshot", {
    appName: "Admin",
    headerTag: "Customer",
    cards: [
      {
        heading: "Identity",
        rows: [
          ["Customer", s.customer],
          ["Email", s.email],
          ["Phone", s.phone],
        ],
      },
      {
        heading: "Billing address",
        paragraphs: [s.address, "Synthetic record for UI and mapping tests only."],
      },
    ],
  }),
  chat_screenshot_png: (s) => phoneArtifact("Screenshot_2026-02-16_0904.png", "CUSTOMER_COMMUNICATION", "Support conversation", "Mobile screenshot", {
    appName: "Support Inbox",
    headerTag: "Conversation",
    messages: [
      { side: "left", text: s.communicationBullets[0] },
      { side: "right", text: s.communicationBullets[1] },
      { side: "left", text: s.communicationBullets[2] },
    ],
  }),
  chat_part_1_png: (s) => phoneArtifact("Screenshot_0711.png", "CUSTOMER_COMMUNICATION", "Support conversation part 1", "Mobile screenshot", {
    appName: "Messages",
    headerTag: "Part 1",
    messages: [
      { side: "left", text: "Customer opened a non-delivery ticket." },
      { side: "right", text: s.communicationBullets[0] },
    ],
  }),
  chat_part_2_png: (s) => phoneArtifact("Screenshot_0712.png", "CUSTOMER_COMMUNICATION", "Support conversation part 2", "Mobile screenshot", {
    appName: "Messages",
    headerTag: "Part 2",
    messages: [
      { side: "left", text: "Support shared the tracking link and ETA." },
      { side: "right", text: s.communicationBullets[1] },
    ],
  }),
  chat_part_3_png: (s) => phoneArtifact("Screenshot_0713.png", "CUSTOMER_COMMUNICATION", "Support conversation part 3", "Mobile screenshot", {
    appName: "Messages",
    headerTag: "Part 3",
    messages: [
      { side: "left", text: "Merchant escalated the carrier investigation." },
      { side: "right", text: s.communicationBullets[2] },
    ],
  }),
  tracking_screenshot_png: (s) => phoneArtifact("carrier_tracking_capture.png", "FULFILLMENT_DELIVERY", "Carrier tracking capture", "Phone screenshot", {
    appName: "Carrier Portal",
    headerTag: "Tracking",
    cards: [
      {
        heading: "Shipment",
        rows: [
          ["Tracking ref", `${s.orderId}-SHIP`],
          ["Destination", s.address],
          ["Status", "Delivered"],
        ],
      },
      {
        heading: "Timeline",
        bullets: s.fulfillmentBullets,
      },
    ],
  }),
  delivery_photo_jpg: (s) => photoArtifact("IMG_1048.JPG", "FULFILLMENT_DELIVERY", "Delivery slip photo", "Phone camera capture", [
    ["Reference", `${s.orderId}-SHIP`],
    ["Destination", s.address],
    ["Observed", "Front desk handoff and signature present"],
  ], [{
    heading: "Visible details",
    bullets: [
      "Synthetic camera photo of a printed delivery confirmation sheet.",
      s.fulfillmentBullets[1],
      s.fulfillmentBullets[2],
    ],
  }], {
    quality: 92,
    deskColor: [219, 212, 198],
    rotation: -2,
  }),
  receipt_gateway_photo_jpg: (s) => photoArtifact("IMG_1052.JPG", "ORDER_RECEIPT", "Gateway receipt photo", "Phone camera capture", [
    ["Order ID", s.orderId],
    ["Transaction ID", s.transactionId],
    ["Captured", s.orderDate],
  ], [{
    heading: "Why this exists",
    bullets: [
      "This second receipt is intentionally redundant.",
      "It simulates a merchant photographing a printed receipt after already uploading an email screenshot.",
    ],
  }], {
    quality: 94,
    deskColor: [211, 206, 191],
    rotation: 1.5,
  }),
  policy_screenshot_png: (s) => phoneArtifact("Screenshot_2026-02-16_0942.png", "POLICIES", "Policy page capture", "Help center screenshot", {
    appName: "Storefront",
    headerTag: "Policy",
    cards: [
      {
        heading: "Refund and delivery policy",
        paragraphs: s.policyParagraphs,
      },
    ],
  }),
  policy_photo_jpg: (s) => photoArtifact("IMG_9004.JPG", "POLICIES", "Policy printout photo", "Phone camera capture", [
    ["Merchant", s.merchant],
    ["Scope", s.reason],
  ], [{
    heading: "Policy summary",
    paragraphs: s.policyParagraphs,
  }], {
    quality: 95,
    deskColor: [201, 185, 166],
    rotation: -1.2,
    oversized: s.id === "shopify_oversized_phone_photos",
  }),
  refund_email_png: (s) => phoneArtifact("Screenshot_2026-01-17_1104.png", "REFUND_CANCELLATION", "Refund confirmation capture", "Inbox screenshot", {
    appName: "Mail",
    headerTag: "Refund",
    cards: [
      {
        heading: "Refund record",
        rows: [
          ["Reference", `${s.orderId}-RFND`],
          ["Customer", s.customer],
          ["Amount", s.amount],
        ],
      },
      {
        heading: "Notes",
        paragraphs: s.refundParagraphs,
      },
    ],
  }),
  return_label_photo_jpg: (s) => photoArtifact("scan0003.jpeg", "CUSTOMER_COMMUNICATION", "Return label and support note", "Phone camera capture", [
    ["Reference", `${s.orderId}-COMM`],
    ["Customer", s.customer],
    ["Channel", "Email and return desk"],
  ], [{
    heading: "Observed details",
    bullets: [
      "Synthetic photo of a printed return or support note captured by phone.",
      s.communicationBullets[0],
      s.communicationBullets[1],
    ],
  }], {
    quality: 93,
    deskColor: [188, 191, 180],
    rotation: 2.4,
  }),
  policy_scan_photo_jpg: (s) => photoArtifact("Terms scan 01.jpg", "POLICIES", "Policy scan photo", "Flatbed or phone scan style", [
    ["Merchant", s.merchant],
    ["Scope", s.reason],
  ], [{
    heading: "Policy summary",
    paragraphs: s.policyParagraphs,
  }], {
    quality: 90,
    deskColor: [191, 196, 203],
    rotation: -0.7,
  }),
  bad_ocr_receipt_photo_jpg: (s) => photoArtifact("IMG_2201.JPG", "ORDER_RECEIPT", "Receipt photo", "Blurry phone capture from camera roll", [
    ["Merchant", s.merchant],
    ["Order ID", s.orderId],
    ["Amount", s.amount],
  ], [{
    heading: "Observed details",
    bullets: [
      "Low-contrast photo captured quickly under uneven light.",
      `Transaction reference ${s.transactionId}`,
      "Receipt text is intentionally harder for OCR to read.",
    ],
  }], {
    quality: 87,
    deskColor: [183, 176, 165],
    rotation: 4.8,
    blur: 2,
    contrast: -0.42,
    brightness: 0.06,
  }),
  bad_ocr_chat_capture_png: (s) => phoneArtifact("IMG_2202.PNG", "CUSTOMER_COMMUNICATION", "Support thread capture", "Low-contrast screenshot export", {
    appName: "Gallery",
    headerTag: "IMG_2202",
    blur: 2,
    contrast: -0.48,
    brightness: 0.08,
    messages: [
      { side: "left", text: s.communicationBullets[0] },
      { side: "right", text: s.communicationBullets[1] },
      { side: "left", text: s.communicationBullets[2] },
    ],
  }),
  bad_ocr_policy_capture_png: (s) => phoneArtifact("IMG_2203.PNG", "POLICIES", "Policy capture", "Washed-out help center screenshot", {
    appName: "Browser",
    headerTag: "IMG_2203",
    blur: 1,
    contrast: -0.45,
    brightness: 0.1,
    cards: [
      {
        heading: "Return and quality policy",
        paragraphs: s.policyParagraphs,
      },
      {
        heading: "Store note",
        paragraphs: ["Synthetic help-center capture with weak contrast and partial blur for OCR testing."],
      },
    ],
  }),
  generic_receipt_capture: (s) => phoneArtifact("document_01.png", "ORDER_RECEIPT", "Receipt capture", "Generic export filename", {
    appName: "Files",
    headerTag: "document_01.png",
    cards: [
      {
        heading: "Payment summary",
        rows: [
          ["Merchant", s.merchant],
          ["Order ID", s.orderId],
          ["Amount", s.amount],
        ],
      },
    ],
  }),
  generic_customer_capture: (s) => photoArtifact("document_02.jpeg", "CUSTOMER_DETAILS", "Customer address capture", "Generic export filename", [
    ["Customer", s.customer],
    ["Email", s.email],
    ["Phone", s.phone],
  ], [{
    heading: "Address",
    paragraphs: [s.address],
  }], {
    quality: 90,
    deskColor: [214, 209, 193],
    rotation: 1.3,
  }),
  generic_chat_capture: (s) => phoneArtifact("document_03.png", "CUSTOMER_COMMUNICATION", "Support message capture", "Generic export filename", {
    appName: "Files",
    headerTag: "document_03.png",
    messages: [
      { side: "left", text: s.communicationBullets[0] },
      { side: "right", text: s.communicationBullets[1] },
    ],
  }),
  oversized_receipt_png: (s) => phoneArtifact("IMG_8832.png", "ORDER_RECEIPT", "Oversized receipt capture", "Phone screenshot straight from camera roll", {
    appName: "Gallery",
    headerTag: "Large PNG",
    width: 1680,
    height: 3400,
    oversized: true,
    cards: [
      {
        heading: "Payment summary",
        rows: [
          ["Merchant", s.merchant],
          ["Order ID", s.orderId],
          ["Transaction ID", s.transactionId],
          ["Amount", s.amount],
        ],
      },
      {
        heading: "Line items",
        bullets: s.itemLines,
      },
      {
        heading: "Why this file is hard",
        bullets: [
          "Large PNG with textured background to simulate screenshot roll exports.",
          "Useful for Shopify file-size and total-budget testing.",
        ],
      },
    ],
  }),
  oversized_delivery_photo_jpg: (s) => photoArtifact("IMG_8833.JPG", "FULFILLMENT_DELIVERY", "Oversized delivery photo", "Phone camera capture", [
    ["Reference", `${s.orderId}-SHIP`],
    ["Destination", s.address],
    ["Observed", "Doorstep and carrier label visible"],
  ], [{
    heading: "Observed details",
    bullets: [
      "Large JPEG with heavy texture to simulate an uncompressed phone photo.",
      s.fulfillmentBullets[0],
      s.fulfillmentBullets[1],
      s.fulfillmentBullets[2],
    ],
  }], {
    quality: 98,
    width: 2400,
    height: 3200,
    oversized: true,
    deskColor: [176, 161, 142],
    rotation: -1.5,
  }),
  camera_receipt_photo_jpg: (s) => photoArtifact("IMG_4021.JPG", "ORDER_RECEIPT", "Printed receipt photo", "Phone camera capture", [
    ["Merchant", s.merchant],
    ["Order ID", s.orderId],
    ["Amount", s.amount],
  ], [{
    heading: "Observed details",
    bullets: [
      "Synthetic phone photo of a paper receipt on a table.",
      "Used to mimic merchants who only have photos instead of exported PDFs.",
    ],
  }], {
    quality: 91,
    deskColor: [184, 172, 160],
    rotation: 2.1,
  }),
};

await fs.mkdir(outputRoot, { recursive: true });

const readme = [
  "# Synthetic Evidence Sets",
  "",
  "These files are fictional demo artifacts for product testing only.",
  "Do not use them in real disputes.",
  "",
  "This folder now mixes `PDF`, `PNG`, and `JPEG` files so the demo sets look closer to real merchant inputs.",
  "",
];

for (const scenario of scenarios) {
  const scenarioDir = path.join(outputRoot, scenario.id);
  await fs.rm(scenarioDir, { recursive: true, force: true });
  await fs.mkdir(scenarioDir, { recursive: true });

  const manifest = {
    id: scenario.id,
    title: scenario.title,
    platform: scenario.platform,
    scope: scenario.scope,
    reason: scenario.reason,
    messyInputs: scenario.messyInputs,
    whyServiceMatters: scenario.whyServiceMatters,
    files: [],
  };

  for (const artifactKey of scenario.docs) {
    const spec = artifactTemplates[artifactKey](scenario);
    const built = await buildArtifact(scenario, spec);
    await fs.writeFile(path.join(scenarioDir, spec.filename), built.bytes);
    manifest.files.push({
      filename: spec.filename,
      evidenceType: spec.evidenceType,
      title: spec.title,
      format: built.format,
      note: spec.subtitle,
    });
  }

  await fs.writeFile(path.join(scenarioDir, "scenario.json"), JSON.stringify(manifest, null, 2) + "\n", "utf8");

  readme.push(`## ${scenario.id}`);
  readme.push("");
  readme.push(`- Platform: ${scenario.platform}`);
  readme.push(`- Scope: ${scenario.scope}`);
  readme.push(`- Reason: ${scenario.reason}`);
  readme.push(`- Messy inputs: ${scenario.messyInputs}`);
  readme.push(`- Why service matters: ${scenario.whyServiceMatters}`);
  for (const file of manifest.files) {
    readme.push(`- ${file.filename} -> ${file.evidenceType} (${file.format})`);
  }
  readme.push("");
}

await fs.writeFile(path.join(outputRoot, "README.md"), readme.join("\n"), "utf8");

async function loadFonts() {
  const load = async (folderName) => loadFont(path.join(fontRoot, folderName, `${folderName}.fnt`));
  return {
    title: await load("open-sans-32-black"),
    body: await load("open-sans-16-black"),
    small: await load("open-sans-14-black"),
    tiny: await load("open-sans-10-black"),
    bodyWhite: await load("open-sans-16-white"),
    titleWhite: await load("open-sans-32-white"),
  };
}

function pdfArtifact(filename, evidenceType, title, subtitle, fields, sections) {
  return {
    kind: "pdf",
    filename,
    evidenceType,
    title,
    subtitle,
    fields,
    sections,
  };
}

function phoneArtifact(filename, evidenceType, title, subtitle, options) {
  return {
    kind: filename.toLowerCase().endsWith(".png") ? "png" : "jpeg",
    layout: "phone",
    filename,
    evidenceType,
    title,
    subtitle,
    ...options,
  };
}

function photoArtifact(filename, evidenceType, title, subtitle, fields, sections, options = {}) {
  return {
    kind: filename.toLowerCase().endsWith(".png") ? "png" : "jpeg",
    layout: "photo",
    filename,
    evidenceType,
    title,
    subtitle,
    fields,
    sections,
    ...options,
  };
}

async function buildArtifact(scenario, spec) {
  if (spec.kind === "pdf") {
    return { bytes: await buildSinglePagePdf(scenario, spec), format: "PDF" };
  }

  const image = spec.layout === "photo"
    ? await buildPhotoCapture(scenario, spec)
    : await buildPhoneCapture(scenario, spec);
  applyImageAdjustments(image, spec);

  if (spec.kind === "jpeg") {
    return {
      bytes: await image.getBuffer(JimpMime.jpeg, { quality: spec.quality ?? 92 }),
      format: "JPEG",
    };
  }
  return {
    bytes: await image.getBuffer(JimpMime.png),
    format: "PNG",
  };
}

function applyImageAdjustments(image, spec) {
  if (spec.blur) {
    image.blur(spec.blur);
  }
  if (typeof spec.contrast === "number") {
    image.contrast(spec.contrast);
  }
  if (typeof spec.brightness === "number") {
    image.brightness(spec.brightness);
  }
  if (spec.greyscale) {
    image.greyscale();
  }
}

async function buildPhoneCapture(scenario, spec) {
  const width = spec.width ?? 1242;
  const height = spec.height ?? (spec.oversized ? 3200 : 2400);
  const image = new Jimp({ width, height, color: rgba(246, 248, 251) });

  paintGrain(image, { x: 0, y: 0, w: width, h: height }, hashSeed(`${scenario.id}:${spec.filename}:body`), [246, 248, 251], spec.oversized ? 20 : 6, spec.oversized ? 2 : 8);
  fillRect(image, 0, 0, width, 84, [19, 28, 44]);
  fillRect(image, 0, 84, width, 128, [255, 255, 255]);
  fillRect(image, 0, 212, width, 3, [217, 223, 232]);

  image.print({ font: fonts.bodyWhite, x: 40, y: 24, text: "9:41", maxWidth: 180 });
  image.print({ font: fonts.small, x: 40, y: 108, text: spec.appName ?? "App", maxWidth: width - 80 });
  image.print({ font: fonts.title, x: 40, y: 138, text: spec.title, maxWidth: width - 80 });

  if (spec.headerTag) {
    drawPill(image, width - 320, 122, 260, spec.headerTag);
  }

  let y = 260;
  if (spec.cards) {
    for (const card of spec.cards) {
      y = drawInfoCard(image, card, 40, y, width - 80) + 26;
    }
  }

  if (spec.messages) {
    y = drawMessageThread(image, spec.messages, 40, y, width - 80) + 26;
  }

  fillRect(image, 30, height - 120, width - 60, 1, [220, 225, 232]);
  image.print({
    font: fonts.tiny,
    x: 40,
    y: height - 96,
    text: `SAMPLE / DEMO ONLY | ${spec.filename} | ${spec.evidenceType}`,
    maxWidth: width - 80,
  });

  return image;
}

async function buildPhotoCapture(scenario, spec) {
  const width = spec.width ?? 1800;
  const height = spec.height ?? 2500;
  const deskColor = spec.deskColor ?? [209, 202, 190];
  const image = new Jimp({ width, height, color: rgba(...deskColor) });
  paintGrain(image, { x: 0, y: 0, w: width, h: height }, hashSeed(`${scenario.id}:${spec.filename}:desk`), deskColor, spec.oversized ? 36 : 14, spec.oversized ? 2 : 6);

  const pageWidth = Math.floor(width * 0.78);
  const pageHeight = Math.floor(height * 0.82);
  const page = new Jimp({ width: pageWidth, height: pageHeight, color: rgba(251, 249, 244) });
  paintGrain(page, { x: 0, y: 0, w: pageWidth, h: pageHeight }, hashSeed(`${scenario.id}:${spec.filename}:page`), [251, 249, 244], spec.oversized ? 10 : 4, 4);

  fillRect(page, 0, 0, pageWidth, 42, [186, 36, 36]);
  page.print({ font: fonts.small, x: 24, y: 10, text: "FICTIONAL TEST EVIDENCE - NOT FOR REAL SUBMISSION", maxWidth: pageWidth - 48 });
  page.print({ font: fonts.title, x: 52, y: 90, text: spec.title, maxWidth: pageWidth - 104 });
  page.print({ font: fonts.body, x: 52, y: 146, text: spec.subtitle, maxWidth: pageWidth - 104 });
  fillRect(page, 52, 196, pageWidth - 104, 2, [220, 226, 233]);

  let y = 238;
  for (const [label, value] of spec.fields ?? []) {
    page.print({ font: fonts.small, x: 52, y, text: label, maxWidth: 220 });
    page.print({ font: fonts.body, x: 300, y, text: String(value), maxWidth: pageWidth - 352 });
    y += 54;
  }

  y += 12;
  for (const section of spec.sections ?? []) {
    const bullets = (section.bullets ?? []).filter(Boolean);
    const paragraphs = (section.paragraphs ?? []).filter(Boolean);
    page.print({ font: fonts.small, x: 52, y, text: section.heading, maxWidth: pageWidth - 104 });
    y += 34;
    if (bullets.length > 0) {
      for (const bullet of bullets) {
        const bulletHeight = measureTextHeight(fonts.body, bullet, pageWidth - 150);
        page.print({ font: fonts.body, x: 80, y, text: `- ${bullet}`, maxWidth: pageWidth - 132 });
        y += bulletHeight + 14;
      }
    }
    if (paragraphs.length > 0) {
      for (const paragraph of paragraphs) {
        const paragraphHeight = measureTextHeight(fonts.body, paragraph, pageWidth - 104);
        page.print({ font: fonts.body, x: 52, y, text: paragraph, maxWidth: pageWidth - 104 });
        y += paragraphHeight + 14;
      }
    }
    y += 12;
  }

  fillRect(page, 52, pageHeight - 112, pageWidth - 104, 1, [220, 226, 233]);
  page.print({
    font: fonts.tiny,
    x: 52,
    y: pageHeight - 90,
    text: `SAMPLE / DEMO ONLY | ${spec.filename} | ${spec.evidenceType}`,
    maxWidth: pageWidth - 104,
  });

  const shadowX = Math.floor((width - pageWidth) / 2) + 18;
  const shadowY = Math.floor((height - pageHeight) / 2) + 26;
  fillRect(image, shadowX, shadowY, pageWidth, pageHeight, [120, 112, 103, 80]);

  if (spec.rotation) {
    page.rotate(spec.rotation);
  }

  const offsetX = Math.floor((width - page.width) / 2);
  const offsetY = Math.floor((height - page.height) / 2);
  image.composite(page, offsetX, offsetY);
  return image;
}

function drawInfoCard(image, card, x, y, width) {
  const bodyWidth = width - 48;
  const rows = card.rows ?? [];
  const bullets = (card.bullets ?? []).filter(Boolean);
  const paragraphs = (card.paragraphs ?? []).filter(Boolean);
  let contentHeight = 32;

  for (const row of rows) {
    contentHeight += 18 + measureTextHeight(fonts.body, String(row[1]), bodyWidth) + 26;
  }
  for (const bullet of bullets) {
    contentHeight += measureTextHeight(fonts.body, bullet, bodyWidth - 24) + 18;
  }
  for (const paragraph of paragraphs) {
    contentHeight += measureTextHeight(fonts.body, paragraph, bodyWidth) + 18;
  }

  const height = Math.max(140, contentHeight + 40);
  fillRect(image, x, y, width, height, [255, 255, 255]);
  strokeRect(image, x, y, width, height, [220, 225, 232]);
  image.print({ font: fonts.small, x: x + 24, y: y + 20, text: card.heading, maxWidth: width - 48 });

  let cursorY = y + 56;
  for (const row of rows) {
    image.print({ font: fonts.tiny, x: x + 24, y: cursorY, text: row[0], maxWidth: 220 });
    cursorY += 18;
    image.print({ font: fonts.body, x: x + 24, y: cursorY, text: String(row[1]), maxWidth: bodyWidth });
    cursorY += measureTextHeight(fonts.body, String(row[1]), bodyWidth) + 22;
    fillRect(image, x + 24, cursorY - 10, width - 48, 1, [235, 239, 244]);
  }
  for (const bullet of bullets) {
    image.print({ font: fonts.body, x: x + 24, y: cursorY, text: `- ${bullet}`, maxWidth: bodyWidth });
    cursorY += measureTextHeight(fonts.body, bullet, bodyWidth - 24) + 18;
  }
  for (const paragraph of paragraphs) {
    image.print({ font: fonts.body, x: x + 24, y: cursorY, text: paragraph, maxWidth: bodyWidth });
    cursorY += measureTextHeight(fonts.body, paragraph, bodyWidth) + 18;
  }

  return y + height;
}

function drawMessageThread(image, messages, x, y, width) {
  const shellHeight = 120 + messages.length * 150;
  fillRect(image, x, y, width, shellHeight, [255, 255, 255]);
  strokeRect(image, x, y, width, shellHeight, [220, 225, 232]);
  image.print({ font: fonts.small, x: x + 24, y: y + 20, text: "Conversation", maxWidth: width - 48 });

  let cursorY = y + 68;
  for (const message of messages) {
    const bubbleWidth = Math.floor(width * 0.72);
    const bubbleX = message.side === "right" ? x + width - bubbleWidth - 24 : x + 24;
    const fill = message.side === "right" ? [48, 118, 255] : [238, 241, 245];
    const textFont = message.side === "right" ? fonts.bodyWhite : fonts.body;
    const height = measureTextHeight(textFont, message.text, bubbleWidth - 28) + 24;
    fillRect(image, bubbleX, cursorY, bubbleWidth, height, fill);
    image.print({ font: textFont, x: bubbleX + 14, y: cursorY + 12, text: message.text, maxWidth: bubbleWidth - 28 });
    cursorY += height + 22;
  }

  return cursorY;
}

function drawPill(image, x, y, width, text) {
  fillRect(image, x, y, width, 48, [225, 236, 255]);
  strokeRect(image, x, y, width, 48, [198, 212, 235]);
  image.print({ font: fonts.small, x: x + 16, y: y + 14, text, maxWidth: width - 32 });
}

function paintGrain(image, area, seed, baseColor, amplitude, blockSize) {
  const rand = createPrng(seed);
  const xStart = clip(Math.floor(area.x), 0, image.width);
  const yStart = clip(Math.floor(area.y), 0, image.height);
  const xEnd = clip(Math.floor(area.x + area.w), 0, image.width);
  const yEnd = clip(Math.floor(area.y + area.h), 0, image.height);
  for (let y = yStart; y < yEnd; y += blockSize) {
    for (let x = xStart; x < xEnd; x += blockSize) {
      const drift = Math.floor((rand() - 0.5) * amplitude);
      const hue = Math.floor((rand() - 0.5) * (amplitude / 2));
      const tone = [
        clip(baseColor[0] + drift + hue, 0, 255),
        clip(baseColor[1] + drift, 0, 255),
        clip(baseColor[2] + drift - hue, 0, 255),
      ];
      fillRect(image, x, y, Math.min(blockSize, xEnd - x), Math.min(blockSize, yEnd - y), tone);
    }
  }
}

function fillRect(image, x, y, w, h, color) {
  const xStart = clip(Math.floor(x), 0, image.width);
  const yStart = clip(Math.floor(y), 0, image.height);
  const width = clip(Math.floor(w), 0, image.width - xStart);
  const height = clip(Math.floor(h), 0, image.height - yStart);
  if (width <= 0 || height <= 0) {
    return;
  }
  const alpha = color[3] ?? 255;
  image.scan(xStart, yStart, width, height, (_x, _y, idx) => {
    image.bitmap.data[idx] = color[0];
    image.bitmap.data[idx + 1] = color[1];
    image.bitmap.data[idx + 2] = color[2];
    image.bitmap.data[idx + 3] = alpha;
  });
}

function strokeRect(image, x, y, w, h, color) {
  fillRect(image, x, y, w, 2, color);
  fillRect(image, x, y + h - 2, w, 2, color);
  fillRect(image, x, y, 2, h, color);
  fillRect(image, x + w - 2, y, 2, h, color);
}

function rgba(r, g, b, a = 255) {
  return ((((r & 255) << 24) >>> 0) | ((g & 255) << 16) | ((b & 255) << 8) | (a & 255)) >>> 0;
}

function clip(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function createPrng(seed) {
  let state = seed >>> 0;
  return () => {
    state = (1664525 * state + 1013904223) >>> 0;
    return state / 4294967296;
  };
}

function hashSeed(value) {
  let hash = 2166136261;
  for (let i = 0; i < value.length; i += 1) {
    hash ^= value.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

async function buildSinglePagePdf(scenario, spec) {
  const pdfDoc = await PDFDocument.create();
  const page = pdfDoc.addPage([612, 792]);
  const font = await pdfDoc.embedFont(StandardFonts.Helvetica);
  const bold = await pdfDoc.embedFont(StandardFonts.HelveticaBold);
  const margin = 54;
  const width = 504;
  let y = 738;

  page.drawText("SAMPLE / DEMO ONLY", {
    x: 150,
    y: 380,
    size: 40,
    font: bold,
    color: rgb(0.92, 0.92, 0.92),
    rotate: degrees(32),
  });

  page.drawRectangle({ x: margin, y, width, height: 22, color: rgb(0.9, 0.16, 0.16) });
  page.drawText("FICTIONAL TEST EVIDENCE - NOT FOR REAL SUBMISSION", {
    x: margin + 8,
    y: y + 7,
    size: 9,
    font: bold,
    color: rgb(1, 1, 1),
  });
  y -= 40;

  page.drawText(spec.title, { x: margin, y, size: 20, font: bold, color: rgb(0.1, 0.12, 0.18) });
  y -= 20;
  page.drawText(spec.subtitle, { x: margin, y, size: 11, font, color: rgb(0.36, 0.39, 0.46) });
  y -= 26;

  page.drawRectangle({
    x: margin,
    y: y - 3,
    width,
    height: 18,
    color: rgb(0.94, 0.97, 1),
    borderColor: rgb(0.8, 0.87, 0.98),
    borderWidth: 1,
  });
  page.drawText(`${scenario.platform} | ${scenario.scope} | ${scenario.reason} | ${spec.evidenceType}`, {
    x: margin + 8,
    y: y + 1,
    size: 10,
    font: bold,
    color: rgb(0.13, 0.28, 0.58),
  });
  y -= 28;

  page.drawText("Key fields", { x: margin, y, size: 12, font: bold, color: rgb(0.2, 0.24, 0.31) });
  y -= 16;

  for (let i = 0; i < spec.fields.length; i += 1) {
    const [label, value] = spec.fields[i];
    page.drawRectangle({
      x: margin,
      y: y - 2,
      width,
      height: 18,
      color: i % 2 === 0 ? rgb(0.98, 0.99, 1) : rgb(1, 1, 1),
      borderColor: rgb(0.9, 0.92, 0.95),
      borderWidth: 0.5,
    });
    page.drawText(label, { x: margin + 8, y: y + 2, size: 10, font: bold, color: rgb(0.22, 0.26, 0.33) });
    page.drawText(String(value), { x: margin + 180, y: y + 2, size: 10, font, color: rgb(0.22, 0.26, 0.33) });
    y -= 18;
  }

  y -= 16;
  for (const section of spec.sections) {
    page.drawText(section.heading, { x: margin, y, size: 12, font: bold, color: rgb(0.12, 0.15, 0.22) });
    y -= 16;
    if (section.bullets) {
      for (const bullet of section.bullets) {
        const lines = wrapText(bullet, font, 10.5, 472);
        page.drawText("-", { x: margin + 4, y, size: 10.5, font, color: rgb(0.24, 0.28, 0.34) });
        for (let i = 0; i < lines.length; i += 1) {
          page.drawText(lines[i], { x: margin + 16, y: y - i * 13, size: 10.5, font, color: rgb(0.24, 0.28, 0.34) });
        }
        y -= lines.length * 13 + 3;
      }
    }
    if (section.paragraphs) {
      for (const paragraph of section.paragraphs) {
        const lines = wrapText(paragraph, font, 10.5, 492);
        for (const line of lines) {
          page.drawText(line, { x: margin, y, size: 10.5, font, color: rgb(0.24, 0.28, 0.34) });
          y -= 13;
        }
        y -= 3;
      }
    }
    y -= 8;
  }

  page.drawLine({ start: { x: margin, y: 42 }, end: { x: 558, y: 42 }, thickness: 0.75, color: rgb(0.86, 0.89, 0.93) });
  page.drawText(`${scenario.id} | ${spec.evidenceType}`, { x: margin, y: 28, size: 8.5, font, color: rgb(0.45, 0.49, 0.56) });
  page.drawText("Page 1 of 1", { x: 500, y: 28, size: 8.5, font, color: rgb(0.45, 0.49, 0.56) });
  return pdfDoc.save();
}

function wrapText(text, font, fontSize, maxWidth) {
  const words = text.split(/\s+/).filter(Boolean);
  const lines = [];
  let current = "";
  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word;
    if (font.widthOfTextAtSize(candidate, fontSize) <= maxWidth) {
      current = candidate;
    } else {
      lines.push(current);
      current = word;
    }
  }
  if (current) {
    lines.push(current);
  }
  return lines;
}
