(ns shadow.graft
  (:require [goog.crypt.base64 :as b64]))

(defmulti scion
  (fn [opts dom]
    (-> opts meta :scion))
  :default ::default)

(defmethod scion ::default [opts dom]
  (js/console.error "UNKNOWN SCION" (:scion (meta opts))))

(defn init
  "looks for graft points and calls them. assumes scions are available and ready.

   encoder is expected to a function taking a string and returning the decoded datastructure
   could be cljs.reader/read-string, a transit read-string equiv or just a js/JSON.parse
   entirely depends on what the server used to encode

   when using multiple modules and shadow.loader use shadow.graft.modules/init instead."
  [decoder]
  (doseq [script (array-seq (js/document.querySelectorAll "script[type=\"shadow/graft\"]"))]
    (let [body
          (.-innerHTML script)

          opts
          (if-not (seq body)
            {}
            (decoder (b64/decodeString body)))

          data-ref
          (.getAttribute script "data-ref")

          dom-ref
          (case data-ref
            "none" nil
            "self" script
            "parent" (.-parentElement script)
            "prev-sibling" (.-previousElementSibling script)
            "next-sibling" (.-nextElementSibling script)
            (throw (ex-info "script tag with invalid dom ref" {:data-ref data-ref :script script})))

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

      (scion opts dom-ref)
      )))
