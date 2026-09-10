(ns gtinverify.store
  "SSoT for the Barcode Verification & Counterfeit-Detection actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section).
  Modeled on cloud-itonami-isco-1324's supplydist.store.

  Domain:

    verified — a committed record {:gtin :seller-id} of the FIRST
               seller a given GTIN was ever cleared for. Written ONLY
               via verify!. A later claim on the same GTIN by a
               DIFFERENT seller is a known-duplicate / reuse signal —
               `gtinverify.governor` checks this independently of
               whatever verdict the advisor proposed.
    ledger    — append-only audit trail, commit (clear) or hold
               (flag/escalate).")

(defprotocol Store
  (verified-record [s gtin])
  (records-of [s seller-id])
  (ledger [s])
  (verify! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (verified-record [_ gtin] (get-in @a [:verified gtin]))
  (records-of [_ seller-id] (filter #(= seller-id (:seller-id %)) (vals (:verified @a))))
  (ledger [_] (:ledger @a))
  (verify! [s record]
    (swap! a assoc-in [:verified (:gtin record)] record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:verified {} :ledger []} seed)))))
