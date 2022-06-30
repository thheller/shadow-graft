(ns shadow.graft.modules
  (:require
    [goog.object :as gobj]
    [shadow.graft :as graft]
    [shadow.loader :as loader]))

;; this is in its own ns so shadow.graft doesn't depend on shadow.loader
;; since that adds 8kb we might not need when not actually using modules

(defn init
  ([encoder]
   (init encoder js/document))
  ([encoder root-element]
   (let [required-modules
         (into #{}
           (for [script (array-seq (.querySelectorAll root-element "script[type=\"shadow/graft\"]"))
                 :let [mod (.getAttribute script "data-module")]
                 :when (seq mod)]
             mod))

         pending-ref
         (atom required-modules)

         load-map
         (loader/load-multiple (into-array required-modules))]

     ;; FIXME: there should be something in loader that handles this
     (doseq [mod required-modules]
       (.addCallback (gobj/get load-map mod)
         (fn []
           (swap! pending-ref disj mod)
           (when-not (seq @pending-ref)
             (graft/init encoder root-element))))))))
