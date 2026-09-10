(ns gtinverify.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [gtinverify.actor :as actor]
            [gtinverify.advisor :as advisor]
            [gtinverify.store :as store]))

(defn- flagging-advisor
  "Test-only advisor stub that always proposes :flag, to exercise the
  advisor-flagged escalation path independent of the mock advisor's
  always-:clear behavior."
  []
  (reify advisor/Advisor
    (-advise [_ _store request]
      {:op :verify-listing :effect :propose :gtin (:gtin request)
       :seller-id (:seller-id request) :verdict :flag :confidence 0.9
       :stake :low :rationale "flagging test stub"})))

(deftest commits-a-well-formed-first-verification
  (let [st (store/mem-store)
        graph (actor/build-graph {:store st})
        request {:gtin "036000291452" :seller-id "seller-1"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "seller-1"))))))

(deftest holds-an-invalid-check-digit-listing
  (let [st (store/mem-store)
        graph (actor/build-graph {:store st})
        request {:gtin "036000291459" :seller-id "seller-1"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "seller-1")))))

(deftest holds-a-known-duplicate-reuse-by-different-seller
  (let [st (store/mem-store)]
    (store/verify! st {:gtin "036000291452" :seller-id "seller-1"})
    (let [graph (actor/build-graph {:store st})
          request {:gtin "036000291452" :seller-id "seller-2"}
          result (actor/run-request! graph request {} "thread-3")]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "seller-2"))))))

(deftest interrupts-on-advisor-flag-then-clears-on-human-approval
  (let [st (store/mem-store)
        graph (actor/build-graph {:store st :advisor (flagging-advisor)})
        request {:gtin "036000291452" :seller-id "seller-1"}
        interrupted (actor/run-request! graph request {} "thread-4")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "seller-1")))
    (let [resumed (actor/approve! graph "thread-4")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "seller-1")))))))
