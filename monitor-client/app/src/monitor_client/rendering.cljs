(ns monitor-client.rendering
  (:require [domina :as dom]
            [domina.css :as dom-css]
            [domina.events :as dom-events]
            [io.pedestal.app.render.push :as render]
            [io.pedestal.app.render.push.templates :as templates]
            [io.pedestal.app.render.push.handlers.automatic :as d]
            [io.pedestal.app.render.push.handlers :as h]
            [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [jayq.core :refer [$]])
  (:require-macros [monitor-client.html-templates :as html-templates]))

(def charts (atom {}))

(defn prepare-chart [id]
  (fn [_ _ _]
    (let [chart ($/plot 
                  id 
                  (clj->js [])
                  (clj->js {:xaxis { :mode "time"}}))]
      (swap! charts assoc id chart))))

(defn render-chart [data max-time id]
  (let [chart (@charts id)
        xaxis (get (aget (.getOptions chart) "xaxes") 0)
        one-minute-ago (- max-time 60000)]
    (aset xaxis "min" one-minute-ago)
    (aset xaxis "max" max-time)
    (.setData chart (clj->js data))
    (.setupGrid chart)
    (.draw chart)))

(defn render-tps-chart [_ [_ _ _ new-value] _]
  (when (not-empty new-value)
    (let [{:keys [received processed]} new-value
          data [{:data received :label "Received TPS"}
                {:data processed :label "Processed TPS"}]
          [last-time _] (last received)]
      (render-chart data last-time "#received_counts"))))

(defn render-backlog-chart [_ [_ _ _ new-value] _]
  (when (not-empty new-value)
    (let [data [{:data new-value :label "Backlog"}]
          [last-time _] (last new-value)]
      (render-chart data last-time "#backlog"))))

(defn render-server-tps-chart [_ [_ _ _ new-value] _]
  (when (not-empty new-value)
    (let [data (map 
                 (fn [[server values]] 
                   {:label (str server " TPS") :data values}) 
                 new-value)
          [server values] (first new-value) 
          [last-time _] (last values)]
      (render-chart data last-time "#server_tps"))))

(defn hide-simulation-params [_ _ _]
  (.hide ($ :#simulation_params)))

(defn get-from-form [state id path]
  (assoc-in state path (cljs.reader/read-string (.val ($ id)))))

(defn update-simulation-state [input-queue]
  (let [state
        (-> {}
          (get-from-form :#received_mean [:received :mean])
          (get-from-form :#received_sigma [:received :sigma])
          (get-from-form :#received_sine_amplitude [:received :sine-amplitude])
          
          (get-from-form :#gabby_mean [:servers "gabby" :mean])
          (get-from-form :#gabby_sigma [:servers "gabby" :sigma])
          
          (get-from-form :#nicky_mean [:servers "nicky" :mean])
          (get-from-form :#nicky_sigma [:servers "nicky" :sigma]))]
    (p/put-message input-queue {msg/type :set
                                msg/topic [:simulation]
                                :value state})))

(defn show-simulation-params [_ _ input-queue]
  (.click 
    ($ :#simulation_update_button)
    #(do 
       (update-simulation-state input-queue)
       (.preventDefault %)))
  (.show ($ :#simulation_params)))

(defn set-on-form [id values path]
  (.val ($ id) (get-in values path)))

(defn set-simulation-params [_ [_ _ _ new-value] _]
  (set-on-form :#received_mean new-value [:received :mean])
  (set-on-form :#received_sigma new-value [:received :sigma])
  (set-on-form :#received_sine_amplitude new-value [:received :sine-amplitude])
  
  (set-on-form :#gabby_mean new-value [:servers "gabby" :mean])
  (set-on-form :#gabby_sigma new-value [:servers "gabby" :sigma])
  
  (set-on-form :#nicky_mean new-value [:servers "nicky" :mean])
  (set-on-form :#nicky_sigma new-value [:servers "nicky" :sigma]))

(defn hide-connect-button [_ _ _]
  (.hide ($ :#connect_button)))

(defn render-config []
  [[:node-create [] hide-simulation-params]
   
   [:node-create [:tps] (prepare-chart "#received_counts")]
   [:value [:tps] render-tps-chart]
   
   [:node-create [:backlog] (prepare-chart "#backlog")]
   [:value [:backlog] render-backlog-chart]
   
   [:node-create [:server-tps] (prepare-chart "#server_tps")]
   [:value [:server-tps] render-server-tps-chart]
   
   [:transform-enable [:simulation] show-simulation-params]
   [:value [:simulation] set-simulation-params]
   
   [:transform-enable [:connected] (h/add-send-on-click "connect_button")]
   [:transform-disable [:connected] hide-connect-button]])
