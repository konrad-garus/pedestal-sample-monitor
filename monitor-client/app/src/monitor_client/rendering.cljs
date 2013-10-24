(ns monitor-client.rendering
  (:require [domina :as dom]
            [io.pedestal.app.render.push :as render]
            [io.pedestal.app.render.push.templates :as templates]
            [io.pedestal.app.render.push.handlers.automatic :as d])
  (:require-macros [monitor-client.html-templates :as html-templates]))

(def received-counts-chart (atom nil))

(defn prepare-received-counts-chart [_ _ _]
  (reset! received-counts-chart ($/plot 
                 "#received_counts" 
                 (clj->js [])
                 (clj->js {:xaxis { :mode "time"}}))))

(defn render-received-counts-chart [_ [_ _ _ new-value] _]
  (let [data new-value
        xaxis (get (aget (.getOptions @received-counts-chart) "xaxes") 0)
        now (.getTime (js/Date.))
        one-minute-ago (- now 60000)]
    (aset xaxis "min" one-minute-ago)
    (aset xaxis "max" now)
    (.setData @received-counts-chart (clj->js [data]))
    (.setupGrid @received-counts-chart)
    (.draw @received-counts-chart)))

(defn render-config []
  [[:node-create [:received-counts] prepare-received-counts-chart]
   [:value [:received-counts] render-received-counts-chart]])
