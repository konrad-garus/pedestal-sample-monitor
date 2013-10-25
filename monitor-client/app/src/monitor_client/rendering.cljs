(ns monitor-client.rendering
  (:require [domina :as dom]
            [io.pedestal.app.render.push :as render]
            [io.pedestal.app.render.push.templates :as templates]
            [io.pedestal.app.render.push.handlers.automatic :as d]
            [io.pedestal.app.render.push.handlers :as h]
            [jayq.core :refer [$]])
  (:require-macros [monitor-client.html-templates :as html-templates]))

(def received-tps-chart (atom nil))

(defn prepare-received-tps-chart [_ _ _]
  (reset! received-tps-chart ($/plot 
                 "#received_counts" 
                 (clj->js [])
                 (clj->js {:xaxis { :mode "time"}}))))

(defn render-received-tps-chart [_ [_ _ _ new-value] _]
  (when (not-empty new-value)
    (let [data new-value
          xaxis (get (aget (.getOptions @received-tps-chart) "xaxes") 0)
          [last-time _] (last new-value)
          one-minute-ago (- last-time 60000)]
      (aset xaxis "min" one-minute-ago)
      (aset xaxis "max" last-time)
      (.setData @received-tps-chart (clj->js [data]))
      (.setupGrid @received-tps-chart)
      (.draw @received-tps-chart))))

(defn hide-connect-button [_ _ _]
  (.hide ($ :#connect_button)))

(defn render-config []
  [[:node-create [:received-tps] prepare-received-tps-chart]
   [:value [:received-tps] render-received-tps-chart]
   
   [:transform-enable [:connected] (h/add-send-on-click "connect_button")]
   [:transform-disable [:connected] hide-connect-button]])

