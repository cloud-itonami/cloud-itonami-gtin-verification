# cloud-itonami-gtin-verification

Open Business Blueprint (implemented actor): **Independent Barcode
Verification & Counterfeit-Detection Service**.

This repository publishes a forkable OSS business for an independent
operator who verifies GTIN-family barcodes (GTIN-8/12/13/14, UPC/EAN/JAN)
for retailers and marketplaces -- flagging invalid check digits and
known-duplicate/reused codes (a GTIN previously verified for a different
seller, a gray-market/counterfeit reuse signal).

## Why this is not code-keyed like ISIC/ISCO/COFOG/UNSPSC blueprints

GTIN is an **identifier system**, not a classification taxonomy (see
ADR-2607031800). `cloud-itonami-gtin-*` splits by FUNCTION instead: this
repo (verification), plus
[`cloud-itonami-gtin-issuance`](https://github.com/cloud-itonami/cloud-itonami-gtin-issuance)
and
[`cloud-itonami-gtin-catalog`](https://github.com/cloud-itonami/cloud-itonami-gtin-catalog).

## No robotics premise

This is a pure data/software verification service (check-digit
computation, duplicate/reuse detection) -- the same digital/data-service
exemption class as
[`cloud-itonami-6310`](https://github.com/cloud-itonami/cloud-itonami-6310).

**Maturity: `:implemented`** -- Verification Advisor ⊣ GTIN Verification
Governor as a langgraph-clj StateGraph (`intake -> advise -> govern ->
decide -> commit/hold`, human-approval interrupt), modeled on
cloud-itonami-isco-1324's supply-distribution actor and sharing
cloud-itonami-gtin-issuance's check-digit implementation pattern. 15
tests / 29 assertions green (`clojure -M:test`).

The one externally-verifiable rule this governor enforces is the REAL
**GS1 GTIN Modulo-10 check-digit algorithm** (GS1 General Specifications
section 7.9 -- the same algorithm underlying every UPC-A / EAN-13(JAN) /
EAN-8 / ITF-14 barcode in circulation). `gtinverify.governor/check-digit`
is verified in `test/gtinverify/governor_test.clj` against two
independently-known reference GTINs: UPC-A `036000291452` (Wrigley's gum,
check digit `2`) and EAN-13 `4006381333931` (GS1's own commonly cited
worked example, check digit `1`).

## Core Contract

```text
listing/product batch + barcode claim (gtin, seller-id)
        |
        v
Verification Advisor -> GTIN Verification Governor -> clear, or hold/human review
        |
        v
verification record (per-item verdict) + audit ledger
```

The governor recomputes its checks independently of whatever verdict the
advisor proposed, so the advisor can never silently clear a listing the
governor's own recompute would flag:

1. **Check-digit correctness** -- the GTIN's own last digit must equal
   the GS1 Modulo-10 check digit computed over the preceding digits (the
   real, cited GS1 standard above). HARD, unconditional hold.
2. **Format** -- the GTIN must be an all-digit string of length 8, 12,
   13 or 14 (the four GS1 GTIN lengths). HARD, unconditional hold.
3. **Known-duplicate** -- the GTIN must not already be verified in this
   actor's own registry for a DIFFERENT seller (reuse/gray-market
   signal -- this actor's own registry invariant, not a fabricated
   external rule). HARD, unconditional hold. The SAME seller
   re-verifying its own already-verified GTIN is fine.
4. **Advisor-flagged** -- if the advisor's own proposed verdict is
   `:flag` (a suspicious-pattern hit the structural checks above can't
   by themselves confirm or deny), the listing always escalates to a
   human reviewer rather than auto-clearing or auto-holding --
   "a `:hit` verdict always forces a hold that a human reviews before
   the listing proceeds."

Also escalates on low confidence (< 0.6).

**Not yet implemented / honestly out of scope:** the advisor's own
pattern-detection logic is a mock stub (`mock-advisor` always proposes
`:clear`; wiring a real LLM or heuristic fraud-pattern detector is left
to `llm-advisor` / a real deployment) -- this repo supplies the governed,
audited scaffold around that detection, not the detection heuristics
themselves.

## Run

```bash
clojure -M:test    # governor contract (incl. the two real-GTIN check-digit
                    # fixtures) + actor lifecycle, 15 tests / 29 assertions
```

## Layout

| File | Role |
|---|---|
| `src/gtinverify/store.cljc` | **Store** protocol -- `MemStore`; per-GTIN first-verified-seller registry, append-only audit ledger. |
| `src/gtinverify/advisor.cljc` | **Verification Advisor** -- `mock-advisor` \\| `llm-advisor`; proposes a clear/flag verdict. |
| `src/gtinverify/governor.cljc` | **GTIN Verification Governor** -- the real GS1 check-digit algorithm + format/known-duplicate HARD checks + advisor-flag/confidence escalation. |
| `src/gtinverify/actor.cljc` | **GTINVerificationActor** -- langgraph-clj StateGraph. |
| `test/gtinverify/*_test.clj` | governor contract (incl. real-GTIN check-digit fixtures) + actor lifecycle. |

## Required capabilities

- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
