(ns ^:shared monitor-client.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app.messages :as msg]
              [io.pedestal.app :as app]))

(def history-entries 60)

(defn set-count [old message]
  (let [{:keys [tstamp value]} message]
    [tstamp value]))

(defn connect [_ message]
  true)

(defn toggle-simulation-running [old-value msg]
  (not old-value))

(defn derive-tps [_ {:keys [old-model new-model input-paths] :as v}]
  (let [input-path (first input-paths)]
    (if-let [[_ old-count] (get-in old-model input-path)]
      (let [[new-time new-count] (get-in new-model input-path)]
        [new-time (- new-count old-count)]))))

(defn derive-history [old val]
  (when val
    (let [history (concat old [val])]
      (take-last history-entries history))))

(defn init-tps-history []
  [[:node-create [:tps] :map]])

(defn emit-tps-history [{:keys [old-model new-model]}]
  (let [recd (get-in new-model [:received :tps-history])
        processed (get-in new-model [:processed :tps-history])
        [last-recd-ts _] (last recd)
        [last-processed-ts _] (last processed)]
    (when (= last-recd-ts last-processed-ts)
      [[:value [:tps] {:received recd :processed processed}]])))

(defn init-connected [arg]
  [[:transform-enable [:connected] :start
    [{msg/topic [:connected] msg/type :start}]]])
    
(defn emit-connected [{:keys [new-model] :as v}]
  (when (get-in new-model [:connected])
    [[:transform-disable [:connected] :start]]))

(defn start-connection [val]
  (when val
    [{msg/topic [:connect] :value true}]))

(def monitor-app
  {:version 2
   :transform [[:set-value [:**] set-count]
               [:start [:connected] connect]]
   :derive #{
             [#{[:received :count]} [:received :tps] derive-tps]
             [#{[:received :tps]} [:received :tps-history] derive-history :single-val]
             [#{[:received :count]} [:received :count-history] derive-history :single-val]
             
             [#{[:processed :count]} [:processed :tps] derive-tps]
             [#{[:processed :tps]} [:processed :tps-history] derive-history :single-val]}
   :emit [{:in #{[:* :tps-history]}
           :fn emit-tps-history
           :init init-tps-history}
          
          {:in #{[:connected]}
           :fn emit-connected
           :init init-connected}
          
          ; For debug/demo only:
          {:in #{[:*]} :fn (app/default-emitter [:model])}]
   
   :effect #{[#{[:connected]} start-connection :single-val]}
   })