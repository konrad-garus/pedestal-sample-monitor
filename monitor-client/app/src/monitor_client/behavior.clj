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

(defn derive-tps [_ {:keys [old-model new-model input-paths]}]
  (let [input-path (first input-paths)]
    (if-let [[_ old-count] (get-in old-model input-path)]
      (let [[new-time new-count] (get-in new-model input-path)]
        [new-time (- new-count old-count)]))))

(defn derive-history [old val]
  (when val
    (let [history (concat old [val])]
      (take-last history-entries history))))

(defn sum-server-counts [_ [time server-counts]]
  [time (reduce #(+ %1 (%2 1)) 0 server-counts)])

(defn server-tps [old {:keys [old-model new-model] :as v}]
  (if-let [[_ old-count] (get-in old-model [:server :count])]
    (let [[new-time new-count] (get-in new-model [:server :count])
          server-names (keys new-count)]
      (loop [servers server-names 
             accumulator {}]
        (let [[server & servers] servers]
          (if server
            (recur servers (assoc accumulator server
                                  (- (new-count server) (old-count server))))
            [new-time accumulator]))))))

(defn derive-backlog [old {:keys [received processed]}]
  (let [[received-ts received-count] received
        [processed-ts processed-count] processed]
    (if (= received-ts processed-ts)
      [received-ts (- received-count processed-count)]
      old)))

(defn init-tps-history []
  [[:node-create [:tps] :map]])

(defn emit-tps-history [{:keys [old-model new-model]}]
  (let [recd (get-in new-model [:received :tps-history])
        processed (get-in new-model [:processed :tps-history])
        [last-recd-ts _] (last recd)
        [last-processed-ts _] (last processed)]
    (when (= last-recd-ts last-processed-ts)
      [[:value [:tps] {:received recd :processed processed}]])))

(defn init-server-history []
  [[:node-create [:server-tps] :map]])

(defn emit-server-history [{:keys [new-model]}]
  (let [history (get-in new-model [:server :tps-history])
        servers (keys (second (first history)))]
    (loop [servers servers, accum {}]
      (let [[server & servers] servers]
        (if server
          (recur 
            servers 
            (assoc accum server (map (fn [[t m]] [t (m server)]) history)))
          [[:value [:server-tps] accum]])))))
  
(defn init-backlog-history []
  [[:node-create [:backlog] :map]])

(defn emit-backlog-history [{:keys [input-paths new-model] :as v}]
  (let [in-path (first input-paths)
        val (get-in new-model in-path)]
    (when val [[:value [:backlog] val]])))

(defn init-connected [arg]
  [[:transform-enable [:connected] :start
    [{msg/topic [:connected] msg/type :start}]]])
    
(defn emit-connected [{:keys [new-model] :as v}]
  (when (get-in new-model [:connected])
    [[:transform-disable [:connected] :start]]))

(defn init-simulation [_]
  [[:transform-disable [:simulation] :update]])

(defn emit-simulation [{:keys [new-model old-model]}]
  (let [new-value [:value [:simulation] (:simulation new-model)]]
    (if (old-model :simulation)
      [new-value]
      [[:transform-enable [:simulation] :update]
       new-value])))

(defn start-connection [val]
  (when val
    [{msg/topic [:connect] :value true}]))

(defn update-simulation-params [val]
  (when val
    [{msg/topic [:simulation] :value val}]))

(def monitor-app
  {:version 2
   :transform [[:set-value [:**] set-count]
               [:set [:**] #(:value %2)]
               [:start [:connected] connect]]
   :derive #{
             [#{[:received :count]} [:received :tps] derive-tps]
             [#{[:received :tps]} [:received :tps-history] derive-history :single-val]
             [#{[:received :count]} [:received :count-history] derive-history :single-val]
             
             [#{[:server :count]} [:processed :count] sum-server-counts :single-val]
             [#{[:server :count]} [:server :tps] server-tps]
             [#{[:server :tps]} [:server :tps-history] derive-history :single-val]
             
             [#{[:processed :count]} [:processed :tps] derive-tps]
             [#{[:processed :tps]} [:processed :tps-history] derive-history :single-val]
             
             [{[:received :count] :received, [:processed :count] :processed} 
              [:backlog :count] 
              derive-backlog :map]
             
             [#{[:backlog :count]} [:backlog :count-history] derive-history :single-val]
             }
   :emit [{:in #{[:received :tps-history] [:processed :tps-history]}
           :fn emit-tps-history
           :init init-tps-history}
          
          {:in #{[:backlog :count-history]}
           :fn emit-backlog-history
           :init init-backlog-history}
          
          {:in #{[:server :tps-history]}
           :fn emit-server-history
           :init init-server-history}
          
          {:in #{[:connected]}
           :fn emit-connected
           :init init-connected}
          
          {:in #{[:simulation]} :fn emit-simulation :init init-simulation}
          
          ; For debug/demo only:
          {:in #{[:*]} :fn (app/default-emitter [:model])}]
   
   :effect #{[#{[:connected]} start-connection :single-val]
             [#{[:simulation]} update-simulation-params :single-val]}
   })