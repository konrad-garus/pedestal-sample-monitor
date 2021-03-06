(ns monitor-client.simulated.start
  (:require [io.pedestal.app.render.push.handlers.automatic :as d]
            [monitor-client.start :as start]
            [monitor-client.rendering :as rendering]
            [goog.Uri]
            ;; This needs to be included somewhere in order for the
            ;; tools to work.
            [io.pedestal.app-tools.tooling :as tooling]
            [io.pedestal.app :as app]
            [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [monitor-client.simulated.services :as services]))

(defn param [name]
  (let [uri (goog.Uri. (.toString  (.-location js/document)))]
    (.getParameterValue uri name)))

(defn ^:export main []
  (let [renderer (param "renderer")
        render-config (if (= renderer "auto")
                        d/data-renderer-config
                        (rendering/render-config))
        app (start/create-app render-config)
        input-queue (get-in app [:app :input])
        services (services/->MockServices (:app app))]
    (app/consume-effects (:app app) services/services-fn)
    (p/start services)
    (.log js/console "Services started")
    app))
