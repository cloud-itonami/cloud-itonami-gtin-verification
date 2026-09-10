(ns gtinverify.actor
  "GTINVerificationActor — the Barcode Verification & Counterfeit-
  Detection actor as a `langgraph.graph/state-graph` (ADR-2607011000 /
  CLAUDE.md Actors section). One graph run = one listing verification
  request (intake → advise → govern → decide → commit/hold, with a
  human-approval interrupt for escalated proposals). No infinite
  internal loop; checkpointed per superstep so an interrupted run can
  resume after human sign-off. Modeled on
  cloud-itonami-isco-1324's supplydist.actor.

  ```text
  :intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true, clears the listing)
                                             +-> :request-approval   (:escalate? true, interrupt-before)
                                             +-> :hold               (:hard? true, structural flag)
  ```

  The unconditional invariant: the Verification Advisor can never
  directly clear a listing the GTINVerificationGovernor would flag —
  every verify! call is gated behind `:decide`, and the governor
  recomputes GS1 check-digit validity and known-duplicate independently
  of whatever verdict the advisor proposed."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [gtinverify.advisor :as advisor]
            [gtinverify.governor :as governor]
            [gtinverify.store :as store]))

(defn build-graph
  "Build a compiled GTINVerificationActor graph. `store` implements
  `gtinverify.store/Store`. `advisor` implements
  `gtinverify.advisor/Advisor` (defaults to `mock-advisor`).
  `checkpointer` defaults to an in-memory one."
  [{:keys [store advisor checkpointer]
    :or {advisor (advisor/mock-advisor)
         checkpointer (cp/mem-checkpointer)}}]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :audit       {:reducer into :default []}}})
      (g/add-node :intake (fn [s] s))
      (g/add-node :advise
                   (fn [{:keys [request]}]
                     (let [p (advisor/-advise advisor store request)]
                       {:proposal p
                        :audit [{:node :advise :request request :proposal p}]})))
      (g/add-node :govern
                   (fn [{:keys [request context proposal]}]
                     (let [v (governor/check request context proposal store)]
                       {:verdict v
                        :audit [{:node :govern :verdict v}]})))
      (g/add-node :decide
                   (fn [{:keys [verdict]}]
                     {:disposition (cond
                                     (:hard? verdict) :hold
                                     (:escalate? verdict) :request-approval
                                     :else :commit)}))
      (g/add-node :request-approval (fn [s] s))
      (g/add-node :commit
                   (fn [{:keys [request proposal]}]
                     (let [record {:gtin (:gtin proposal)
                                    :seller-id (:seller-id proposal)
                                    :payload proposal}]
                       (store/verify! store record)
                       (store/append-ledger! store {:disposition :commit :record record})
                       {:record record
                        :audit [{:node :commit :record record}]})))
      (g/add-node :hold
                   (fn [{:keys [verdict]}]
                     (store/append-ledger! store {:disposition :hold :verdict verdict})
                     {:audit [{:node :hold :verdict verdict}]}))
      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)
      (g/add-conditional-edges
       :decide
       (fn [{:keys [disposition]}]
         (case disposition
           :commit :commit
           :request-approval :request-approval
           :hold)))
      (g/add-edge :request-approval :commit)
      (g/set-finish-point :commit)
      (g/set-finish-point :hold)
      (g/compile-graph {:checkpointer checkpointer
                         :interrupt-before #{:request-approval}})))

(defn run-request!
  "Run one verification request to completion or interrupt.
  `thread-id` scopes checkpointing for resume after human approval."
  [graph request context thread-id]
  (g/run* graph {:request request :context context} {:thread-id thread-id}))

(defn approve!
  "Human-in-the-loop resume: the interrupted `:request-approval` node
  advances straight to `:commit` on resume (approval is the act of
  resuming the thread)."
  [graph thread-id]
  (g/run* graph nil {:thread-id thread-id :resume? true}))
