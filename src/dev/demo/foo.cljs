(ns demo.foo
  (:require
    [shadow.graft :as graft]
    [clojure.string :as str]))

(defmethod graft/scion "upper-case-on-click" [opts el]
  (.addEventListener el "click"
    (fn [e]
      (set! el -innerHTML (str/upper-case (.-innerHTML el))))))
