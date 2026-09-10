(ns gtinverify.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [gtinverify.store :as store]
            [gtinverify.governor :as governor]))

(defn- propose
  ([gtin seller-id] (propose gtin seller-id :clear))
  ([gtin seller-id verdict]
   {:op :verify-listing :effect :propose :gtin gtin :seller-id seller-id
    :verdict verdict :confidence 0.9 :stake :low}))

(def ^:private req {})

;; --- check-digit correctness, verified against two independently-known
;; --- real-world GTINs (not fabricated test fixtures) -- same fixtures
;; --- as cloud-itonami-gtin-issuance's governor_test.clj.

(deftest check-digit-matches-real-upc-a
  (testing "Wrigley's gum UPC-A 036000291452, check digit is 2"
    (is (= 2 (governor/check-digit [0 3 6 0 0 0 2 9 1 4 5])))))

(deftest check-digit-matches-real-ean-13
  (testing "GS1's own worked example 4006381333931, check digit is 1"
    (is (= 1 (governor/check-digit [4 0 0 6 3 8 1 3 3 3 9 3])))))

(deftest valid-gtin-accepts-real-upc-a
  (is (governor/valid-gtin? "036000291452")))

;; --- governor contract

(deftest ok-first-verification-of-a-well-formed-gtin
  (let [st (store/mem-store)
        v (governor/check req {} (propose "036000291452" "seller-1") st)]
    (is (:ok? v))))

(deftest hard-on-invalid-check-digit
  (let [st (store/mem-store)
        v (governor/check req {} (propose "036000291459" "seller-1") st)]
    (is (:hard? v))
    (is (some #(= :invalid-check-digit (:rule %)) (:violations v)))))

(deftest hard-on-invalid-length
  (let [st (store/mem-store)
        v (governor/check req {} (propose "12345" "seller-1") st)]
    (is (:hard? v))
    (is (some #(= :invalid-length (:rule %)) (:violations v)))))

(deftest hard-on-known-duplicate-different-seller
  (let [st (store/mem-store)]
    (store/verify! st {:gtin "036000291452" :seller-id "seller-1"})
    (let [v (governor/check req {} (propose "036000291452" "seller-2") st)]
      (is (:hard? v))
      (is (some #(= :known-duplicate (:rule %)) (:violations v))))))

(deftest ok-same-seller-re-verifying-own-gtin
  (let [st (store/mem-store)]
    (store/verify! st {:gtin "036000291452" :seller-id "seller-1"})
    (let [v (governor/check req {} (propose "036000291452" "seller-1") st)]
      (is (:ok? v))
      (is (not (:hard? v))))))

(deftest hard-on-no-actuation-violation
  (let [st (store/mem-store)
        v (governor/check req {} (assoc (propose "036000291452" "seller-1") :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-advisor-flagged-listing
  (let [st (store/mem-store)
        v (governor/check req {} (propose "036000291452" "seller-1" :flag) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (store/mem-store)
        v (governor/check req {} (assoc (propose "036000291452" "seller-1") :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
