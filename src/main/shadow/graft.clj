(ns shadow.graft
  (:require
    [clojure.java.io :as io]
    [clojure.edn :as edn])
  (:import [org.apache.commons.text StringEscapeUtils]))


(def valid-refs #{:none :self :parent :next-sibling :prev-sibling})

(declare service?)

(defn add
  ([svc id]
   (add svc id :none {}))
  ([svc id stock-ref]
   (add svc id stock-ref {}))
  ([{:keys [encoder manifest-ref] :as svc} id stock-ref opts]
   {:pre [(service? svc)
          (string? id)
          (contains? valid-refs stock-ref)
          (map? opts)]}

   (str "<script type=\"shadow/graft\" data-id=\"" id "\" data-ref=\"" (name stock-ref) "\""
        (when-let [mod-info (get @manifest-ref id)]
          (str " data-module=\"" (:mod mod-info) "\""))
        ">"
        (when (seq opts)
          ;; escape html to prevent some XSS
          (StringEscapeUtils/escapeHtml4 (encoder opts)))
        "</script>")))

(defrecord Service [encoder manifest-ref]
  clojure.lang.IFn
  (invoke [this id]
    (add this id))
  (invoke [this id stock-ref]
    (add this id stock-ref))
  (invoke [this id stock-ref opts]
    (add this id stock-ref opts)))

(defn service? [x]
  (instance? Service x))

(comment
  (add (start) "foo-toggle" :parent {:hello "world"})
  )

(defn set-manifest [{:keys [manifest-ref] :as svc} new-manifest]
  (reset! manifest-ref new-manifest)
  svc)

(defn use-manifest-resource [svc path-to-manifest]
  (set-manifest svc (edn/read-string (slurp (io/resource path-to-manifest)))))

(defn start
  "encoder is expected to be a single arity function to turn the opts datastructure used in add to a string
   defaults to just pr-str but could be using transit if both the frontend and backend prefer it"
  ([]
   (start pr-str))
  ([encoder]
   ;; since there are various possible sources for this we just start empty
   ;; and let secondary services (eg. shadow.graft.fs-watch) fill it
   (->Service encoder (atom {}))))

(comment
  (-> (start pr-str)
      (use-manifest-resource "public/js/graft.edn")))

(defn stop [svc])

;; CLJS macro. kinda bad this is in this namespace since the CLJS code is not otherwise
;; involved in the code above. should be fine as long as this namespace doesn't get more complex

(defmacro reloadable [& body]
  (if (not= :release (:shadow.build/mode &env))
    `(shadow.graft/reloadable* (fn [] ~@body))
    `(do ~@body)))