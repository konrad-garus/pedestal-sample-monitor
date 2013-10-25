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

(defn derive-received-count-history [old val]
  (let [history (concat old [val])]
    (take-last history-entries history)))

(defn init-recd-count-history []
  [[:node-create [:received-counts] :map]])

(defn emit-recd-count-history [{:keys [old-model new-model]}]
  [[:value [:received-counts] (get-in new-model [:received :count-history])]])

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
             [#{[:received :count]} [:received :count-history] derive-received-count-history :single-val]}
   :emit [{:in #{[:received :count-history]}
           :fn emit-recd-count-history
           :init init-recd-count-history}
          
          {:in #{[:connected]}
           :fn emit-connected
           :init init-connected}
          
          ; For debug/demo only:
          {:in #{[:*]} :fn (app/default-emitter [:model])}]
   
   :effect #{[#{[:connected]} start-connection :single-val]}
   })