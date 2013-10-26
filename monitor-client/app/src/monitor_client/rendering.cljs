(ns monitor-client.rendering
  (:require [domina :as dom]
            [io.pedestal.app.render.push :as render]
            [io.pedestal.app.render.push.templates :as templates]
            [io.pedestal.app.render.push.handlers.automatic :as d]
            [io.pedestal.app.render.push.handlers :as h]
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

(defn hide-connect-button [_ _ _]
  (.hide ($ :#connect_button)))

(defn render-config []
  [[:node-create [:tps] (prepare-chart "#received_counts")]
   [:value [:tps] render-tps-chart]
   
   [:node-create [:backlog] (prepare-chart "#backlog")]
   [:value [:backlog] render-backlog-chart]
   
   [:node-create [:server-tps] (prepare-chart "#server_tps")]
   [:value [:server-tps] render-server-tps-chart]
   
   [:transform-enable [:connected] (h/add-send-on-click "connect_button")]
   [:transform-disable [:connected] hide-connect-button]])

