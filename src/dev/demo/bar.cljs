(ns demo.bar
  (:require
    [shadow.graft :as graft]))

(defmethod graft/scion "disappearing-link" [opts link]
  (.addEventListener link "click"
    (fn [e]
      (.preventDefault e)
      (.remove link))))
