# Business Model: Independent Barcode Verification & Counterfeit-Detection Service

## Classification

- Repository: `cloud-itonami-gtin-verification`
- Domain: product-identity / GTIN verification (function-keyed, not
  code-keyed -- see README)
- Social impact: fewer counterfeit/gray-market listings reaching
  consumers, cleaner retailer catalogs

## Customer

- online marketplaces vetting new seller listings
- retailers onboarding new SKUs from unfamiliar suppliers
- brand owners monitoring for unauthorized resale of duplicated barcodes

## Offer

- batch barcode verification (check-digit validity)
- duplicate/reuse detection against the operator's own verification
  history
- suspicious-pattern flagging (e.g. sequential codes from an unverified
  source)
- human-review escalation for any flagged listing (never auto-cleared,
  never auto-blocked without review)
- verification-history audit export for marketplace trust & safety teams

## Revenue

- per-batch verification fee
- marketplace/retailer subscription (recurring onboarding verification)
- brand-protection monitoring subscription

## Trust Controls

- every verdict requires GTIN Verification Governor clearance
- a `:hit` (invalid/duplicate/suspicious) verdict is a hard hold -- never
  auto-cleared
- every verification run and hold is logged
- public counterfeit-detection claims must reference the audit ledger
