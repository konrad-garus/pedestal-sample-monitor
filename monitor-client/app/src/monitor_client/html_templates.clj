(ns monitor-client.html-templates
  (:use [io.pedestal.app.templates :only [tfn dtfn tnodes]]))

(defmacro monitor-client-templates
  []
  ;; Extract the 'hello' template from the template file monitor-client.html.
  ;; The 'dtfn' function will create a dynamic template which can be
  ;; updated after it has been attached to the DOM.
  ;;
  ;; To see how this template is used, refer to
  ;;
  ;; app/src/monitor_client/rendering.cljs
  ;;
  ;; The last argument to 'dtfn' is a set of fields that should be
  ;; treated as static fields (may only be set once). Dynamic templates
  ;; use ids to set values so you cannot dynamically set an id.
  {:monitor-client-page (dtfn (tnodes "monitor-client.html" "hello") #{:id})})

;; Note: this file will not be reloaded automatically when it is changed.
