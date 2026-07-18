(ns gtinverify.advisor
  "Verification Advisor — proposes a clear/flag verdict for a listing's
  claimed GTIN. Swappable mock/llm; the advisor ONLY proposes —
  `gtinverify.governor` independently recomputes GS1 check-digit
  validity and known-duplicate/reuse against the registered store, so
  the advisor can never silently clear something the governor would
  flag. Modeled on cloud-itonami-isco-1324's supplydist.advisor.

  A proposal: {:op :verify-listing :effect :propose :gtin str
               :seller-id str :verdict :clear|:flag :confidence n
               :stake kw :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [gtin seller-id stake] :as request}]
  {:op :verify-listing
   :effect :propose
   :gtin gtin
   :seller-id seller-id
   :verdict :clear
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed clear for GTIN " gtin " / seller " seller-id)})

(defn mock-advisor
  "Always proposes :clear (the governor independently recomputes the
  structural checks regardless of what the advisor proposes)."
  []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a barcode verification advisor. Given a listing's claimed
   GTIN and seller, propose a :verdict of :clear or :flag, an honest
   :confidence and a :stake. Never clear a GTIN with an invalid GS1
   check digit or one already verified for a different seller — the
   governor independently recomputes both against the registered
   store regardless of your verdict.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :verdict :flag :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :verdict :flag :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "verification request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
