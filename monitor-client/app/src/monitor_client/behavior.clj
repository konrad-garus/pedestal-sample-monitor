(ns ^:shared monitor-client.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app.messages :as msg]
              [io.pedestal.app :as app]))

(defn set-received-count [old message]
  (let [{:keys [tstamp value]} message]
    [tstamp value]))

(def monitor-app
  {:version 2
   :transform [[:set-value [:received :count] set-received-count]]})