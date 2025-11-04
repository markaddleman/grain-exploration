(ns grain-exploration.add-numbers
  (:require [ai.obney.grain.event-store-v2.interface :as es]
            [ai.obney.grain.schema-util.interface :refer [defschemas]]
            [ai.obney.grain.behavior-tree-v2.interface :as bt :refer [st-memory-has-value?]]))

(defschemas event-schemas
  {:arithmetic/addition-performed
   [:map
    [:num1 :int]
    [:num2 :int]
    [:result :int]]

   :does-this-work?
   [:map
    [:message :string]]})

(defn add-numbers-fn [context]
  (let [st-memory-atom (:st-memory context)
        {:keys [num1 num2]} @st-memory-atom]
    (if (and num1 num2)
      (do (swap! st-memory-atom assoc :result (+ num1 num2))
          :success)
      :failure)))

(defn next-pair-fn [context]
  (let [st-memory-atom (:st-memory context)
        {:keys [pairs current-index]} @st-memory-atom
        next-index (inc current-index)]
    (if (< next-index (count pairs))
      (do (swap! st-memory-atom assoc :current-index next-index)
          :success)
      :failure)))

(defn append-result-fn [context]
  (let [st-memory-atom (:st-memory context)
        {:keys [result results]} @st-memory-atom]
    (if result
      (do (swap! st-memory-atom update :results (fnil conj []) result)
          (swap! st-memory-atom dissoc :result)             ; clear for next iteration
          :success)
      :failure)))

(require '[ai.obney.grain.behavior-tree-v2.interface.protocol :as btp])

(defmethod btp/tick :loop
  [{:keys [children] :as _node} context]
  (loop []
    (let [result (btp/tick (first children) context)]
      (if (= result btp/failure)
        btp/success
        (if (= result btp/success)
          (recur)
          result)))))

(defmethod btp/build :loop
  [node-type args]
  (let [[opts children] (btp/opts+children args)]
    (assoc opts
      :type node-type
      :children (mapv #(btp/build (first %) (rest %)) children))))

(defmethod btp/tick :invoke
  [{:keys [tree input-fn output-path opts] :as _node} context]
  (let [input-state (input-fn context)
        sub-bt (bt/build tree {:event-store (:event-store context) :st-memory input-state})
        result (bt/run sub-bt)]
    (if (= result btp/success)
      (do (when output-path
            (let [output-val (get-in @(:st-memory (:context sub-bt)) output-path)]
              (when-let [st-mem (:st-memory context)]
                (swap! st-mem assoc-in output-path output-val))))
          btp/success)
      btp/failure)))

(defmethod btp/build :invoke
  [node-type args]
  (let [[opts children] (btp/opts+children args)]
    (assoc opts
      :type node-type
      :tree (first children)
      :input-fn (:input-fn opts)
      :output-path (:output-path opts))))

(defn run-until-completion [built-tree]
  (bt/run built-tree)
  (into [] (:results @(:st-memory (:context built-tree)))))

(def add-numbers-tree
  [:sequence
   [:condition
    {:path   [:num1]
     :schema :int}
    st-memory-has-value?]

   [:condition
    {:path   [:num2]
     :schema :int}
    st-memory-has-value?]

   [:action
    {:id :add}
    add-numbers-fn]

   [:condition
    {:path   [:result]
     :schema :int}
    st-memory-has-value?]])

(def batch-add-numbers-tree
  [:sequence
   [:condition
    {:path   [:pairs]
     :schema [:vector :map]}
    st-memory-has-value?]

   [:condition
    {:path   [:current-index]
     :schema :int}
    st-memory-has-value?]

   [:loop
    [:sequence
     [:invoke
      {:input-fn    (fn [ctx]
                      (let [{:keys [pairs current-index]} @(:st-memory ctx)
                            current-pair (get pairs current-index)]
                        {:num1 (:num1 current-pair) :num2 (:num2 current-pair)}))
       :output-path [:result]}
      add-numbers-tree]

     [:condition
      {:path   [:result]
       :schema :int}
      st-memory-has-value?]

     [:action
      {:id :append}
      append-result-fn]

     [:action
      {:id :next}
      next-pair-fn]]]])

(comment

  ;; Single pair example
  (do
    (def event-store (es/start {:conn {:type :in-memory}}))
    (def single-add-bt (bt/build add-numbers-tree {:event-store event-store :st-memory {:num1 5 :num2 3}}))
    (bt/run single-add-bt))

  ;; Multiple pairs example (auto-complete)
  (do
    (def event-store (es/start {:conn {:type :in-memory}}))
    (def batch-add-bt (bt/build batch-add-numbers-tree
                                {:event-store event-store
                                 :st-memory   {:pairs         [{:num1 5 :num2 3}
                                                               {:num1 10 :num2 7}
                                                               {:num1 2 :num2 8}]
                                               :current-index 0}}))
    (run-until-completion batch-add-bt)

    ;; View results
    (clojure.pprint/pprint @(:st-memory (:context batch-add-bt)))

    ;; View event log
    (clojure.pprint/pprint (into [] (es/read event-store {}))))

  "")
