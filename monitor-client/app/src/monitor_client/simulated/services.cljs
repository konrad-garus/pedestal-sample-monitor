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

(def processed-counters (atom {"gabby" 0 "nicky" 0}))

(def frequency-ms 1000)

(def simulation-params (atom {:received {:mean 10 
                                         :sigma 2
                                         :sine-amplitude 5}
                              :servers {"gabby" {:mean 6
                                                 :sigma 3}
                                        "nicky" {:mean 3
                                                 :sigma 1}}}))

(def connected (atom false))

(defn advance-state []
  (swap! step + .5)
  (let [{:keys [mean sigma sine-amplitude]} (:received @simulation-params)
        mean-sine (+ mean (* sine-amplitude (Math/sin @step)))]
    (swap! received-count + (positive (rand-normal-int mean-sine sigma))))
  
  (let [received-count @received-count 
        processed-sum (reduce #(+ %1 (%2 1)) 0 @processed-counters)]
    (loop [to-process (- received-count processed-sum) servers (keys @processed-counters)]
      (let [[server & servers] servers
            val (@processed-counters server)
            {:keys [mean sigma]} (get-in @simulation-params [:servers server])
            delta (->
                    (rand-normal-int mean sigma)
                    positive
                    (Math/min to-process))]
        (swap! processed-counters assoc server (+ val delta))
        (when-not (empty? servers)
          (recur (- to-process delta) servers))))))

(defn receive-messages [input-queue]
  (advance-state)
  (let [ts (.getTime (js/Date.))
        ts-seconds (* (int (/ ts 1000)) 1000)]
    (when @connected
      (p/put-message input-queue {msg/type :set-value
                                  msg/topic [:received :count]
                                  :value @received-count :tstamp ts-seconds })
      (p/put-message input-queue {msg/type :set-value
                                  msg/topic [:server :count]
                                  :tstamp ts-seconds
                                  :value @processed-counters})))
  
  (platform/create-timeout frequency-ms #(receive-messages input-queue)))

(defrecord MockServices [app]
  p/Activity
  (start [this]
    (receive-messages (:input app)))
  (stop [this]))

(defn push-simulation-params [input-queue]
  (p/put-message input-queue {msg/type :set
                              msg/topic [:simulation]
                              :value @simulation-params}))

(defn connection-established [message input-queue]
  (reset! connected (:value message))
  (push-simulation-params input-queue))

(defn services-fn [message input-queue]
  (.log js/console (str "Sending message to server: " message))
  (case (msg/topic message)
    [:connect] (connection-established message input-queue)
    [:simulation] (reset! simulation-params (:value message)))) 
