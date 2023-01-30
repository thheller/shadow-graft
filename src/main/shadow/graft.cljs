(ns shadow.graft
  (:require-macros [shadow.graft])
  (:require
    [goog.string :as gstr]))

(defmulti scion
  (fn [opts dom]
    (-> opts meta :scion))
  :default ::default)

(defmethod scion ::default [opts dom]
  (js/console.error "UNKNOWN SCION" (:scion (meta opts))))

(defn get-dom-ref [script data-ref]
  (case data-ref
    "none" nil
    "self" script
    "parent" (.-parentElement script)
    "prev-sibling" (.-previousElementSibling script)
    "next-sibling" (.-nextElementSibling script)
    (throw (ex-info "script tag with invalid dom ref" {:data-ref data-ref :script script}))))

(defn run-script-tag [decoder script]
  (let [body
        (.-innerHTML script)

        opts
        (if-not (seq body)
          {}
          (decoder (gstr/unescapeEntities body)))

        data-ref
        (.getAttribute script "data-ref")

        dom-ref
        (get-dom-ref script data-ref)

        scion-id
        (.getAttribute script "data-id")

        ;; FIXME: meta or just add to opts with namespaced keywords?
        opts
        (with-meta opts
          {:scion scion-id
           :script script
           :data-ref data-ref
           :dom-ref dom-ref})]

    (when ^boolean js/goog.DEBUG
      (js/console.log "scion init" scion-id script opts))

    (scion opts dom-ref)))

(defn init
  "looks for graft points and calls them. assumes scions are available and ready.

   encoder is expected to a function taking a string and returning the decoded datastructure
   could be cljs.reader/read-string, a transit read-string equiv or just a js/JSON.parse
   entirely depends on what the server used to encode

   when using multiple modules and shadow.loader use shadow.graft.modules/init instead."
  ([decoder]
   (init decoder js/document))
  ([decoder ^js root-element]

   ;; safeguard so nobody gets any ideas to call this repeatedly for hot-reload or so
   (when (.-graftDidInit root-element)
     (throw (ex-info "graft/init is only supposed to run once per element, calling it again is not allowed." {})))

   (set! root-element -graftDidInit true)

   (-> (.querySelectorAll root-element "script[type=\"shadow/graft\"]")
       ;; should be safe to assume every browser supports forEach on NodeList nowadays right?
       (.forEach #(run-script-tag decoder %)))))

;; utility for hot-reload since scions are only supposed to run once on page init
;; but the code they run might want to get hot-reloaded. there is intentionally
;; no way to remove or update them since the stock DOM tree doesn't change once loaded

;; we cannot generally assume that all scions are hot-reloadable
;; if they are they need to tell us

(defonce reloadables #js [])

(defn reloadable*
  [^function thunk]
  {:pre (fn? thunk)}
  ;; only need this in development, for release just call it which
  ;; for :advanced builds this will all mostly disappear anyways
  (when ^boolean js/goog.DEBUG
    (.push reloadables thunk))
  (thunk))

(defn reloadable
  "THIS IS A MACRO, DON'T CALL AS FUNCTION!

 if parts of a scion are supposed to update on hot-reload they can provide a callback here

 ;; server hiccup
 (defn ui-app []
   (html
     [:div]
     (graft \"my-app\" :prev-sibling {:foo \"bar\"})))

 ;; client
 (defmethod graft/scion \"my-app\" [opts container]
   (do-stuff-once)
   (graft/reloadable
     (rdom/render [ui-app opts] container)))

 the reloadable part must ensure that container actually remains in the DOM. if it is
 removed the reloadable part will fail. since the server-generated HTML tree can't
 change there is no need to refresh opts or container otherwise.

 without the reloadable call this would just render once and not update with the
 regular hot-reload cycle."
  [body])

(defn ^:dev/after-load reload! []
  (.forEach reloadables (fn [^function thunk] (thunk))))

