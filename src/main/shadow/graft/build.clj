(ns shadow.graft.build
  "provides a shadow-cljs build hook to find declared scions and the modules they end up in"
  (:require
    [shadow.build.data :as data]
    [clojure.string :as str]))


(def defmethod-needle "shadow.graft.scion.cljs$core$IMultiFn$_add_method$arity$3(null,\"")

;; defmethod leaves no trace in the analyzer data, so there is no record of known defmethod dispatch vals.
;; instead we just comb through the generated JS trying to find calls. not ideal but should be fine.

(defn find-scions [js]
  (loop [from-idx 0
         scions #{}]
    (let [idx (str/index-of js defmethod-needle from-idx)]
      (if-not idx
        scions
        (let [dispatch-start (+ idx (count defmethod-needle))
              dispatch-end (str/index-of js "\"," dispatch-start)
              dispatch (subs js dispatch-start dispatch-end)]
          (recur (+ 2 dispatch-end) (conj scions dispatch))
          )))))

(comment
  ;; code generated from (defmethod graft/scion "foo-toggle" [opts dom])
  ;; supposed to find them all
  (find-scions
    (str "shadow.graft.scion.cljs$core$IMultiFn$_add_method$arity$3(null,\"foo-toggle\",(function (opts,dom){\nreturn null;\n}));"
         "yo"
         "shadow.graft.scion.cljs$core$IMultiFn$_add_method$arity$3(null,\"bar-toggle\",(function (opts,dom){\nreturn null;\n}));"
         "x"
         "shadow.graft.scion.cljs$core$IMultiFn$_add_method$arity$3(null,\"baz-toggle\",(function (opts,dom){\nreturn null;\n}));")
    )
  )

(defn hook
  {:shadow.build/stage :flush}
  [{:keys [build-sources] :as build-state}]

  (let [state
        (reduce
          (fn [build-state src-id]
            (let [rc (get-in build-state [:sources src-id])]
              ;; only process cljs resources that required shadow.graft
              (if-not (and (= :cljs (:type rc))
                           (contains? (:requires rc) 'shadow.graft))
                build-state
                ;; FIXME: store this info so it doesn't run on every watch cycle?
                (let [{:keys [js]} (get-in build-state [:output src-id])
                      scions (find-scions js)
                      mod (get-in build-state [:compiler-env :shadow.lazy/ns->mod (:ns rc)])]

                  (reduce
                    (fn [build-state scion]
                      (assoc-in build-state [::manifest scion] {:mod mod :ns (:ns rc)}))
                    build-state
                    scions)))))
          (assoc build-state ::manifest {})
          build-sources)]

    (spit
      ;; FIXME: make it configurable where this goes?
      (data/output-file state "graft.edn")
      (::manifest state))

    state))
