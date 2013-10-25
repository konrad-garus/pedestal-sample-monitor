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

(def processed-count (atom 0))
(def processed-mean 10)
(def processed-sigma 1)

(def frequency-ms 1000)

(def connected (atom false))

(defn advance-state []
  (swap! step + .5)
  (let [received-mean (+ received-mean (* 5 (Math/sin @step)))]
    (swap! received-count + (positive (rand-normal-int received-mean received-sigma))))
  (let [to-process (- @received-count @processed-count)
        delta (positive (rand-normal-int processed-mean processed-sigma))
        delta (Math/min delta to-process)]
    (swap! processed-count + delta)))

(defn receive-messages [input-queue]
  (advance-state)
  (let [ts (.getTime (js/Date.))
        ts-seconds (* (int (/ ts 1000)) 1000)]
    (when @connected
      (p/put-message input-queue {msg/type :set-value
                                  msg/topic [:received :count]
                                  :value @received-count :tstamp ts-seconds })
      (p/put-message input-queue {msg/type :set-value
                                  msg/topic [:processed :count]
                                  :value @processed-count :tstamp ts-seconds })))
  
  (platform/create-timeout frequency-ms #(receive-messages input-queue)))

(defrecord MockServices [app]
  p/Activity
  (start [this]
    (receive-messages (:input app)))
  (stop [this]))

(defn services-fn [message input-queue]
  (.log js/console (str "Sending message to server: " message))
  (case (msg/topic message)
    [:connect] (reset! connected (:value message))))
