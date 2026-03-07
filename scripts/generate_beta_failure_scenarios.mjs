import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { PDFDocument, StandardFonts, rgb } from "pdf-lib";
import { Jimp, JimpMime, loadFont, rgbaToInt } from "jimp";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "..");
const outputRoot = path.join(repoRoot, "output", "pdf", "beta-failure-scenarios-2026-03-07");
const fontRoot = path.join(repoRoot, "node_modules", "@jimp", "plugin-print", "fonts", "open-sans");

const fonts = await loadFonts();

const scenarios = [
  {
    id: "ocr_ready_free_summary",
    title: "OCR-ready Stripe INR pack",
    notes: [
      "Create a Stripe / STRIPE_DISPUTE / PRODUCT_NOT_RECEIVED case.",
      "Upload every file in this folder.",
      "Expected: document_01.png should map to ORDER_RECEIPT via OCR text, PDFs should map via content text, export should unlock, unpaid summary PDF should download with watermark."
    ],
    files: [
      screenshotSpec("document_01.png", "Receipt screenshot", [
        "Order receipt",
        "Order number NW-9001",
        "Payment total $84.20",
        "Visa ending 4242"
      ]),
      pdfSpec("customer_profile.pdf", "Customer profile", [
        "Customer details",
        "Customer name: Avery Morgan",
        "Billing address: 880 Market Street Apt 8",
        "Email address: avery@example-demo.com"
      ]),
      pdfSpec("delivery_proof.pdf", "Carrier timeline", [
        "Tracking number 9400 1234 5678 9001",
        "Delivered to front desk on 2026-02-13",
        "Carrier: UPS",
        "Shipment proof retained for dispute review"
      ]),
      screenshotSpec("chat_followup.png", "Customer follow-up", [
        "Customer support",
        "Hi, where is my order?",
        "Tracking says delivered but nothing was received.",
        "Merchant escalated a carrier trace."
      ])
    ]
  },
  {
    id: "gif_auto_convert_free_summary",
    title: "GIF receipt auto-convert pack",
    notes: [
      "Create a Stripe / STRIPE_DISPUTE / PRODUCT_NOT_RECEIVED case.",
      "Upload every file in this folder.",
      "Expected: receipt_capture.gif should show auto-convert messaging, upload should store it as JPEG, and export should unlock with the free watermarked summary PDF."
    ],
    files: [
      gifScreenshotSpec("receipt_capture.gif", "Receipt screenshot", [
        "Order receipt",
        "Order number GIF-7001",
        "Payment total $71.00",
        "Visa ending 1881"
      ]),
      pdfSpec("customer_profile.pdf", "Customer profile", [
        "Customer details",
        "Customer name: Reese Harper",
        "Billing address: 411 Summer Avenue",
        "Email address: reese@example-demo.com"
      ]),
      pdfSpec("delivery_proof.pdf", "Carrier delivery proof", [
        "Tracking number 9400 5555 0000 7001",
        "Delivered on 2026-02-18",
        "Carrier: FedEx",
        "Driver scan confirmed destination city"
      ])
    ]
  },
  {
    id: "duplicate_receipt_autofix",
    title: "Duplicate receipt auto-fix pack",
    notes: [
      "Create a Stripe / STRIPE_DISPUTE / PRODUCT_NOT_RECEIVED case.",
      "Upload every file in this folder.",
      "Expected: two receipt PDFs trigger duplicate evidence type; auto-fix should merge or clear the blocker."
    ],
    files: [
      pdfSpec("receipt_a.pdf", "Gateway receipt", [
        "Order receipt",
        "Invoice for order DUP-2001",
        "Subtotal $49.00",
        "Payment captured successfully"
      ]),
      pdfSpec("receipt_b.pdf", "Email receipt copy", [
        "Receipt confirmation",
        "Order number DUP-2001",
        "Total paid $49.00",
        "Confirmation email sent"
      ]),
      pdfSpec("customer_profile.pdf", "Customer profile", [
        "Customer details",
        "Customer name: Jamie Park",
        "Billing address: 18 Orchard Lane",
        "Phone number: +1 415 555 0102"
      ]),
      pdfSpec("delivery_proof.pdf", "Delivery proof", [
        "Tracking number 1Z DUP 2001",
        "Delivered on 2026-02-15",
        "Carrier scan matched destination city"
      ])
    ]
  },
  {
    id: "shopify_credit_total_size_over_limit",
    title: "Shopify credit total-size over-limit pack",
    notes: [
      "Create a Shopify / SHOPIFY_CREDIT_DISPUTE case.",
      "Upload every file in this folder.",
      "Expected: total-size blocker with a ranked replacement list on validate. Auto-fix should reduce size but the case may still need better evidence composition."
    ],
    files: [
      noisyImageSpec("camera_roll_1.png", 1500, 1500, 11),
      noisyImageSpec("camera_roll_2.png", 1500, 1500, 19),
      screenshotSpec("customer_capture.png", "Customer account", [
        "Customer profile",
        "Name: Leah Patel",
        "Billing profile verified",
        "Address on file matches order"
      ])
    ]
  },
  {
    id: "stripe_total_size_autofix_then_missing_delivery",
    title: "Stripe total-size then missing-delivery pack",
    notes: [
      "Create a Stripe / STRIPE_DISPUTE / PRODUCT_NOT_RECEIVED case.",
      "Upload the two PNG files and map one to ORDER_RECEIPT and the other to CUSTOMER_DETAILS if needed.",
      "Expected: validate starts blocked on total size, auto-fix reduces the pack, then validate should point to delivery proof as the next required evidence."
    ],
    files: [
      noisyImageSpec("camera_roll_receipt.png", 1300, 1300, 42),
      noisyImageSpec("camera_roll_customer.png", 1300, 1300, 84)
    ]
  },
  {
    id: "support_chat_tracking_conflict",
    title: "Support chat with tracking language",
    notes: [
      "Create a Stripe / STRIPE_DISPUTE / PRODUCT_NOT_RECEIVED case.",
      "Upload every file in this folder.",
      "Expected: the support chat screenshot should map to CUSTOMER_COMMUNICATION, not FULFILLMENT_DELIVERY, even though it contains tracking and delivered language."
    ],
    files: [
      pdfSpec("receipt.pdf", "Order receipt", [
        "Order receipt",
        "Order number CHAT-4104",
        "Amount charged $58.40",
        "Merchant checkout confirmed"
      ]),
      pdfSpec("customer_profile.pdf", "Customer profile", [
        "Customer details",
        "Customer name: Alexis Reed",
        "Billing address: 77 Harbor View",
        "Email address: alexis@example-demo.com"
      ]),
      pdfSpec("delivery_proof.pdf", "Carrier timeline", [
        "Tracking number 9400 CHAT 4104",
        "Delivered to apartment parcel room",
        "Carrier scan timestamp 2026-02-22 17:18"
      ]),
      screenshotSpec("chat_followup.png", "Support chat", [
        "Customer support",
        "Hi, where is my order?",
        "Tracking says delivered but nothing arrived.",
        "Merchant opened a carrier trace."
      ])
    ]
  },
  {
    id: "mixed_manual_mapping_bundle",
    title: "Mixed manual mapping bundle",
    notes: [
      "Create a Stripe / STRIPE_DISPUTE / PRODUCT_NOT_RECEIVED case.",
      "Upload every file in this folder.",
      "Expected: generic screenshots, OCR text, content PDFs, and oversized image hints all mix without collapsing into OTHER_SUPPORTING."
    ],
    files: [
      screenshotSpec("document_01.png", "Order page", [
        "Order receipt",
        "Checkout summary",
        "Total paid $118.40",
        "Order number MIX-4104"
      ]),
      screenshotSpec("document_02.png", "Chat thread", [
        "Chat with support",
        "Customer asked for tracking update",
        "Merchant replied with carrier link",
        "Support promised follow-up"
      ]),
      pdfSpec("tracking_update.pdf", "Tracking update", [
        "Tracking number 9400 MIX 4104",
        "Shipment accepted by carrier",
        "Out for delivery",
        "Delivered to lobby desk"
      ]),
      pdfSpec("refund_policy.pdf", "Return terms", [
        "Refund policy",
        "Return policy",
        "Cancellation policy",
        "Merchant policies for damaged deliveries"
      ]),
      noisyImageSpec("camera_roll_3.png", 1300, 1300, 27)
    ]
  },
  {
    id: "noise_fallback_manual_review",
    title: "Noisy image should stay supporting",
    notes: [
      "Create a Stripe / STRIPE_DISPUTE / PRODUCT_NOT_RECEIVED case.",
      "Upload every file in this folder.",
      "Expected: the noisy camera-roll image should stay OTHER_SUPPORTING instead of filling a required evidence gap by default."
    ],
    files: [
      pdfSpec("receipt.pdf", "Order receipt", [
        "Order receipt",
        "Order number NOISE-2102",
        "Amount charged $101.90",
        "Customer checkout confirmed"
      ]),
      screenshotSpec("chat_thread.png", "Chat with support", [
        "Customer support",
        "Customer requested delivery update",
        "Merchant promised a response",
        "No resolution yet"
      ]),
      noisyImageSpec("camera_noise.png", 1300, 1300, 91)
    ]
  },
  {
    id: "stripe_total_pages_over_limit",
    title: "Stripe total-pages over-limit pack",
    notes: [
      "Create a Stripe / STRIPE_DISPUTE / PRODUCT_NOT_RECEIVED case.",
      "Upload every file in this folder.",
      "Expected: validate should block on total PDF pages and rank the longest PDF first."
    ],
    files: [
      multiPagePdfSpec("delivery_dump.pdf", "Delivery event export", 52, (pageNumber) => [
        "Tracking export page " + pageNumber,
        "Tracking number 9400 PAGES 5201",
        "Carrier event log copied from merchant portal",
        "This oversized export intentionally exceeds the page limit."
      ]),
      pdfSpec("receipt.pdf", "Order receipt", [
        "Order receipt",
        "Order number PAGES-5201",
        "Amount charged $35.10",
        "Merchant checkout confirmed"
      ]),
      pdfSpec("customer_profile.pdf", "Customer profile", [
        "Customer details",
        "Customer name: Morgan Tate",
        "Billing address: 2 Mission Plaza",
        "Email address: morgan@example-demo.com"
      ])
    ]
  },
  {
    id: "stripe_total_pages_autofix_duplicates",
    title: "Stripe duplicate-page auto-fix pack",
    notes: [
      "Create a Stripe / STRIPE_DISPUTE / PRODUCT_NOT_RECEIVED case.",
      "Upload every file in this folder.",
      "Expected: validate starts blocked on total pages, but auto-fix should remove duplicate PDF pages and unlock export."
    ],
    files: [
      multiPagePdfSpec(
        "delivery_dump_duplicates.pdf",
        "Delivery event export",
        50,
        (pageNumber) => pageNumber <= 46
          ? [
              "Tracking export page " + pageNumber,
              "Tracking number 9400 DUPPAGES 5202",
              "Carrier event log copied from merchant portal"
            ]
          : [
              "Duplicate appendix page",
              "Same export appendix copied multiple times",
              "Same export appendix copied multiple times"
            ],
        { showPageNumbers: false }
      ),
      pdfSpec("receipt.pdf", "Order receipt", [
        "Order receipt",
        "Order number DUPPAGES-5202",
        "Amount charged $41.20",
        "Merchant checkout confirmed"
      ]),
      pdfSpec("customer_profile.pdf", "Customer profile", [
        "Customer details",
        "Customer name: Dana Ortiz",
        "Billing address: 10 Harbor Point",
        "Email address: dana@example-demo.com"
      ])
    ]
  },
  {
    id: "external_link_pdf_autofix",
    title: "External-link PDF auto-fix pack",
    notes: [
      "Create a Stripe / STRIPE_DISPUTE / PRODUCT_NOT_RECEIVED case.",
      "Upload every file in this folder.",
      "Expected: the linked PDF should trigger the external-links blocker, auto-fix should clear it, and export should become available."
    ],
    files: [
      pdfSpec("receipt.pdf", "Order receipt", [
        "Order receipt",
        "Order number LINK-1188",
        "Amount charged $90.00",
        "Merchant checkout confirmed"
      ]),
      pdfSpec("customer_profile.pdf", "Customer profile", [
        "Customer details",
        "Customer name: Riley Chen",
        "Billing address: 911 King Street",
        "Email address: riley@example-demo.com"
      ]),
      pdfSpec("delivery_proof.pdf", "Carrier delivery proof", [
        "Tracking number 9400 LINK 1188",
        "Delivered to front porch",
        "Carrier scan timestamp 2026-02-24 15:22"
      ]),
      linkedPdfSpec("merchant_portal_export.pdf")
    ]
  }
];

await fs.rm(outputRoot, { recursive: true, force: true });
await fs.mkdir(outputRoot, { recursive: true });

for (const scenario of scenarios) {
  const scenarioDir = path.join(outputRoot, scenario.id);
  await fs.mkdir(scenarioDir, { recursive: true });
  await fs.writeFile(path.join(scenarioDir, "README.txt"), scenario.notes.join("\n") + "\n", "utf8");
  for (const spec of scenario.files) {
    const bytes = await buildArtifact(spec);
    await fs.writeFile(path.join(scenarioDir, spec.filename), bytes);
  }
}

console.log(`Generated ${scenarios.length} beta failure scenario folders under ${outputRoot}`);

function pdfSpec(filename, title, lines) {
  return { kind: "pdf", filename, title, pages: [lines] };
}

function multiPagePdfSpec(filename, title, pageCount, pageBuilder, options = {}) {
  return {
    kind: "pdf",
    filename,
    title,
    pages: Array.from({ length: pageCount }, (_, index) => pageBuilder(index + 1)),
    showPageNumbers: options.showPageNumbers !== false
  };
}

function linkedPdfSpec(filename) {
  return { kind: "linked-pdf", filename };
}

function screenshotSpec(filename, title, lines) {
  return { kind: "png", filename, title, lines, width: 1170, height: 2532, style: "screenshot" };
}

function gifScreenshotSpec(filename, title, lines) {
  return { kind: "gif", filename, title, lines, width: 1170, height: 2532, style: "screenshot" };
}

function noisyImageSpec(filename, width, height, seed) {
  return { kind: "png", filename, title: "Camera roll capture", lines: [], width, height, style: "noise", seed };
}

async function buildArtifact(spec) {
  if (spec.kind === "pdf") {
    return buildPdf(spec);
  }
  if (spec.kind === "linked-pdf") {
    return buildLinkedPdf();
  }
  return buildImage(spec);
}

async function buildPdf(spec) {
  const document = await PDFDocument.create();
  const font = await document.embedFont(StandardFonts.Helvetica);
  const bold = await document.embedFont(StandardFonts.HelveticaBold);

  for (const [index, lines] of spec.pages.entries()) {
    const page = document.addPage([612, 792]);
    page.drawRectangle({ x: 36, y: 740, width: 540, height: 28, color: rgb(0.91, 0.16, 0.16) });
    page.drawText("FICTIONAL TEST EVIDENCE - NOT FOR REAL SUBMISSION", {
      x: 48,
      y: 748,
      size: 11,
      font: bold,
      color: rgb(1, 1, 1)
    });

    const heading = spec.pages.length > 1 && spec.showPageNumbers !== false
      ? `${spec.title} - Page ${index + 1}`
      : spec.title;
    page.drawText(heading, {
      x: 48,
      y: 700,
      size: 22,
      font: bold,
      color: rgb(0.11, 0.13, 0.18)
    });

    let y = 660;
    for (const line of lines) {
      page.drawText(line, {
        x: 52,
        y,
        size: 12,
        font,
        color: rgb(0.17, 0.2, 0.27)
      });
      y -= 22;
    }

    page.drawText("SAMPLE / DEMO ONLY", {
      x: 48,
      y: 42,
      size: 10,
      font: bold,
      color: rgb(0.45, 0.48, 0.54)
    });
  }

  return document.save();
}

async function buildLinkedPdf() {
  const rawPdf = `%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj << /Type /Pages /Count 1 /Kids [3 0 R] >>
endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] /Annots [4 0 R] >>
endobj
4 0 obj << /Type /Annot /Subtype /Link /Rect [72 180 240 198] /A << /S /URI /URI (https://example.com/merchant-portal) >> >>
endobj
trailer << /Root 1 0 R >>
%%EOF
`;
  return Buffer.from(rawPdf, "latin1");
}

async function buildImage(spec) {
  if (spec.style === "noise") {
    const image = new Jimp({ width: spec.width, height: spec.height, color: rgba(236, 239, 243) });
    const seed = spec.seed ?? 1;
    for (let y = 0; y < spec.height; y++) {
      for (let x = 0; x < spec.width; x++) {
        const noise = pseudoRandom(seed, x, y);
        image.setPixelColor(rgba(noise, 255 - noise, (noise * 3) % 255), x, y);
      }
    }
    return image.getBuffer(JimpMime.png);
  }

  const image = new Jimp({ width: spec.width, height: spec.height, color: rgba(246, 248, 251) });
  fillRect(image, 0, 0, spec.width, 96, [15, 23, 42]);
  fillRect(image, 0, 96, spec.width, 120, [255, 255, 255]);
  image.print({ font: fonts.smallWhite, x: 42, y: 30, text: "9:41", maxWidth: 180 });
  image.print({ font: fonts.small, x: 42, y: 118, text: spec.title, maxWidth: spec.width - 84 });

  let y = 260;
  for (const line of spec.lines) {
    fillRect(image, 42, y - 18, spec.width - 84, 88, [255, 255, 255]);
    image.print({ font: fonts.title, x: 70, y, text: line, maxWidth: spec.width - 140 });
    y += 120;
  }

  image.print({
    font: fonts.tiny,
    x: 42,
    y: spec.height - 88,
    text: `SAMPLE / DEMO ONLY | ${spec.filename}`,
    maxWidth: spec.width - 84
  });

  if (spec.kind === "gif") {
    return image.getBuffer(JimpMime.gif);
  }
  return image.getBuffer(JimpMime.png);
}

async function loadFonts() {
  const load = async (folderName) => loadFont(path.join(fontRoot, folderName, `${folderName}.fnt`));
  return {
    title: await load("open-sans-32-black"),
    small: await load("open-sans-16-black"),
    smallWhite: await load("open-sans-16-white"),
    tiny: await load("open-sans-10-black")
  };
}

function fillRect(image, x, y, width, height, rgbColor) {
  const color = rgba(...rgbColor);
  for (let row = y; row < y + height; row++) {
    for (let col = x; col < x + width; col++) {
      image.setPixelColor(color, col, row);
    }
  }
}

function rgba(r, g, b, a = 255) {
  return rgbaToInt(r, g, b, a);
}

function pseudoRandom(seed, x, y) {
  const value = Math.sin((x + 1) * 12.9898 + (y + 1) * 78.233 + seed * 0.45) * 43758.5453;
  return Math.floor((value - Math.floor(value)) * 255);
}
