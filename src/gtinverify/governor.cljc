(ns gtinverify.governor
  "GTINVerificationGovernor — the independent invariant layer for the
  Barcode Verification & Counterfeit-Detection actor (itonami actor
  pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled on
  cloud-itonami-isco-1324's supplydist.governor.

  The one externally-verifiable rule this governor enforces is REAL:
  the GS1 GTIN Modulo-10 check-digit algorithm (GS1 General
  Specifications section 7.9, 'GTIN Check Digit Calculation' — the
  same algorithm underlying every UPC-A / EAN-13(JAN) / EAN-8 / ITF-14
  barcode in circulation). `check-digit` below is verified against two
  independently-known reference GTINs: UPC-A 036000291452 (Wrigley's
  gum, check digit 2) and EAN-13 4006381333931 (GS1's own commonly
  cited worked example, check digit 1) — both round-trip correctly
  (see `governor_test.clj`).

  The governor recomputes these checks independently of whatever
  verdict the advisor proposed, so the advisor can never silently
  clear a listing the governor's own recompute would flag.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. no-actuation           — proposal :effect must be :propose.
    2. gtin format              — the GTIN must be an all-digit string
                                of length 8, 12, 13 or 14 (the four
                                GS1 GTIN lengths).
    3. check-digit correctness   — the GTIN's own last digit must equal
                                the GS1 Modulo-10 check digit computed
                                over the preceding digits (REAL, cited
                                standard — see ns docstring).
    4. known-duplicate            — the GTIN is already `verified` in
                                the store for a DIFFERENT seller than
                                the one making this claim (reuse/
                                gray-market signal — set membership
                                against this actor's own registry, the
                                same category as
                                cloud-itonami-isco-1324's carrier-
                                membership check, not a fabricated
                                external rule).
  ESCALATION invariants (:escalate? true, human sign-off — 'a :hit
  verdict always forces a hold that a human reviews before the
  listing proceeds'):
    5. advisor-flagged           — the advisor's own proposed :verdict
                                is :flag (a suspicious-pattern hit the
                                governor's structural checks cannot by
                                themselves confirm or deny).
    6. low confidence (< `confidence-floor`)."
  (:require [gtinverify.store :as store]))

(def confidence-floor 0.6)
(def ^:private valid-lengths #{8 12 13 14})

(defn- digit-string? [s]
  (boolean (and (string? s) (seq s) (re-matches #"\d+" s))))

(defn- digits [s]
  (mapv #(- (int %) (int \0)) s))

(defn check-digit
  "GS1 Modulo-10 check digit (GS1 General Specifications section 7.9).
  `payload-digits` is the GTIN without its own check digit — a
  collection of ints, most-significant digit first. Multiply the
  digit immediately left of the (absent) check digit by 3, alternate
  1/3 moving left, sum, and the check digit is whatever brings the
  sum to the next multiple of 10."
  [payload-digits]
  (let [weighted (->> payload-digits
                       reverse
                       (map-indexed (fn [i d] (* d (if (even? i) 3 1)))))
        total (reduce + weighted)]
    (mod (- 10 (mod total 10)) 10)))

(defn valid-gtin?
  "True if `gtin` is a digit-only string of a valid GTIN length AND
  its own last digit matches the GS1 check digit computed over the
  rest."
  [gtin]
  (and (digit-string? gtin)
       (contains? valid-lengths (count gtin))
       (let [ds (digits gtin)]
         (= (last ds) (check-digit (butlast ds))))))

(defn- hard-violations [{:keys [proposal]} existing-record]
  (let [{:keys [gtin seller-id]} proposal
        well-formed? (and (digit-string? gtin) (contains? valid-lengths (count gtin)))]
    (cond-> []
      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (not well-formed?)
      (conj {:rule :invalid-length
             :detail (str "GTIN は数字のみ・8/12/13/14桁である必要がある (" (pr-str gtin) ")")})

      (and well-formed? (not (valid-gtin? gtin)))
      (conj {:rule :invalid-check-digit
             :detail "GS1 Modulo-10 check digit 不一致（GS1 General Specifications §7.9）"})

      (and existing-record (not= seller-id (:seller-id existing-record)))
      (conj {:rule :known-duplicate
             :detail (str "GTIN " gtin " は既に別 seller (" (:seller-id existing-record)
                          ") で verified 済み -- 転用/gray-market の疑い")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `gtinverify.store/Store`. Pure — never mutates
  the store."
  [request context proposal store]
  (let [gtin (:gtin proposal)
        existing (when gtin (store/verified-record store gtin))
        hard (hard-violations {:proposal proposal} existing)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        advisor-flagged? (= :flag (:verdict proposal))]
    {:ok? (and (not hard?) (not low?) (not advisor-flagged?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? advisor-flagged?))}))
