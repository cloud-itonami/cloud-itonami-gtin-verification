# cloud-itonami-gtin-verification

Open Business Blueprint: **Independent Barcode Verification &
Counterfeit-Detection Service**.

This repository designs a forkable OSS business for an independent
operator who verifies GTIN-family barcodes (GTIN-8/12/13/14, UPC/EAN/JAN)
for retailers and marketplaces -- flagging invalid check digits,
duplicate/reused codes, and patterns consistent with counterfeit or
gray-market listings.

## Why this is not code-keyed like ISIC/ISCO/COFOG/UNSPSC blueprints

GTIN is an **identifier system**, not a classification taxonomy (see
ADR-2607031800). `cloud-itonami-gtin-*` splits by FUNCTION instead: this
repo (verification), plus
[`cloud-itonami-gtin-issuance`](https://github.com/cloud-itonami/cloud-itonami-gtin-issuance)
and
[`cloud-itonami-gtin-catalog`](https://github.com/cloud-itonami/cloud-itonami-gtin-catalog).

## No robotics premise

This is a pure data/software verification service (check-digit
computation, duplicate/pattern detection) -- the same digital/data-service
exemption class as
[`cloud-itonami-6310`](https://github.com/cloud-itonami/cloud-itonami-6310).

## Core Contract

```text
listing/product batch + barcode claims
        |
        v
Verification Advisor -> GTIN Verification Governor -> clear, flag, or human review
        |
        v
verification record (per-item verdict) + audit ledger
```

No automated verdict can clear a listing the governor would flag (invalid
check digit, known-duplicate, suspicious reuse pattern) without governor
approval and audit evidence -- a `:hit` verdict always forces a hold that a
human reviews before the listing proceeds.

## Required capabilities

- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
