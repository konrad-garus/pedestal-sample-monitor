(ns ^:shared monitor-client.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app.messages :as msg]
              [io.pedestal.app :as app]))

(def history-entries 60)

(defn set-received-count [old message]
  (let [{:keys [tstamp value]} message]
    [tstamp value]))

(defn connect [_ message]
  true)

(defn toggle-simulation-running [old-value msg]
  (not old-value))

(defn derive-received-tps [_ {:keys [old-model new-model]}]
  (if-let [[_ old-count] (get-in old-model [:received :count])]
    (let [[new-time new-count] (get-in new-model [:received :count])]
      [new-time (- new-count old-count)])))

(defn derive-history [old val]
  (when val
    (let [history (concat old [val])]
      (take-last history-entries history))))

(defn init-recd-tps-history []
  [[:node-create [:received-tps] :map]])

(defn emit-recd-tps-history [{:keys [old-model new-model]}]
  [[:value [:received-tps] (get-in new-model [:received :tps-history])]])

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
   :transform [[:set-value [:received :count] set-received-count]
               [:start [:connected] connect]]
   :derive #{
             [#{[:received :count]} [:received :tps] derive-received-tps]
             [#{[:received :tps]} [:received :tps-history] derive-history :single-val]
             [#{[:received :count]} [:received :count-history] derive-history :single-val]}
   :emit [{:in #{[:received :tps-history]}
           :fn emit-recd-tps-history
           :init init-recd-tps-history}
          
          {:in #{[:connected]}
           :fn emit-connected
           :init init-connected}
          
          ; For debug/demo only:
          {:in #{[:*]} :fn (app/default-emitter [:model])}]
   
   :effect #{[#{[:connected]} start-connection :single-val]}
   })