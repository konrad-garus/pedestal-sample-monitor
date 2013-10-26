(ns monitor-client.rendering
  (:require [domina :as dom]
            [io.pedestal.app.render.push :as render]
            [io.pedestal.app.render.push.templates :as templates]
            [io.pedestal.app.render.push.handlers.automatic :as d]
            [io.pedestal.app.render.push.handlers :as h]
            [jayq.core :refer [$]])
  (:require-macros [monitor-client.html-templates :as html-templates]))

(def tps-chart (atom nil))

(def backlog-chart (atom nil))

(defn prepare-tps-chart [_ _ _]
  (reset! tps-chart ($/plot 
                 "#received_counts" 
                 (clj->js [])
                 (clj->js {:xaxis { :mode "time"}}))))

(defn render-tps-chart [_ [_ _ _ new-value] _]
  (when (not-empty new-value)
    (let [{:keys [received processed]} new-value
          data [{:data received :label "Received TPS"}
                {:data processed :label "Processed TPS"}]
          xaxis (get (aget (.getOptions @tps-chart) "xaxes") 0)
          [last-time _] (last received)
          one-minute-ago (- last-time 60000)]
      (aset xaxis "min" one-minute-ago)
      (aset xaxis "max" last-time)
      (.setData @tps-chart (clj->js data))
      (.setupGrid @tps-chart)
      (.draw @tps-chart))))

(defn prepare-backlog-chart [_ _ _]
  (reset! backlog-chart ($/plot 
                 "#backlog" 
                 (clj->js [])
                 (clj->js {:xaxis { :mode "time"}}))))

(defn render-backlog-chart [_ [_ _ _ new-value] _]
  (when (not-empty new-value)
    (let [data [{:data new-value :label "Backlog"}]
          xaxis (get (aget (.getOptions @backlog-chart) "xaxes") 0)
          [last-time _] (last new-value)
          one-minute-ago (- last-time 60000)]
      (aset xaxis "min" one-minute-ago)
      (aset xaxis "max" last-time)
      (.setData @backlog-chart (clj->js data))
      (.setupGrid @backlog-chart)
      (.draw @backlog-chart))))

(defn hide-connect-button [_ _ _]
  (.hide ($ :#connect_button)))

(defn render-config []
  [[:node-create [:tps] prepare-tps-chart]
   [:value [:tps] render-tps-chart]
   
   [:node-create [:backlog] prepare-backlog-chart]
   [:value [:backlog] render-backlog-chart]
   
   [:transform-enable [:connected] (h/add-send-on-click "connect_button")]
   [:transform-disable [:connected] hide-connect-button]])

