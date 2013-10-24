(ns monitor-client.simulated.services
  (:require [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [io.pedestal.app.util.platform :as platform]))

(defn rand-normal [mean sigma]
  (let [val-standard  (*(Math/sqrt (- (* 2 (Math/log (rand))))) (Math/cos (* 2 Math/PI (rand))))]
    (+ (* sigma val-standard) mean)))

(defn rand-normal-int [mean sigma]
  (int (rand-normal mean sigma)))

(def step (atom 0))

(defn positive [n] (Math/max n 0))

(def received-count (atom 0))

(def received-mean 10)

(def received-sigma 2)

(def frequency-ms 1000)

(defn advance-state []
  (swap! step + .5)
  (let [received-mean (+ received-mean (* 5 (Math/sin @step)))]
    (swap! received-count + (positive (rand-normal-int received-mean received-sigma)))))

(defn receive-messages [input-queue]
  (advance-state)
  (let [ts (.getTime (js/Date.))
        ts-seconds (* (int (/ ts 1000)) 1000)]
    (p/put-message input-queue {msg/type :set-value
                                msg/topic [:received :count]
                                :value @received-count :tstamp ts-seconds }))
  
  (platform/create-timeout frequency-ms #(receive-messages input-queue)))

(defrecord MockServices [app]
  p/Activity
  (start [this]
    (receive-messages (:input app)))
  (stop [this]))
