(ns ^:shared monitor-client.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app.messages :as msg]
              [io.pedestal.app :as app]))

(def history-entries 5)

(defn set-received-count [old message]
  (let [{:keys [tstamp value]} message]
    [tstamp value]))

(defn derive-received-count-history [old val]
  (let [history (concat old [val])]
    (take-last history-entries history)))

(def monitor-app
  {:version 2
   :transform [[:set-value [:received :count] set-received-count]]
   :derive #{
             [#{[:received :count]} [:received :count-history] derive-received-count-history :single-val]}
   })