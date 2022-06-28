(ns demo.app
  (:require
    [shadow.graft.modules :as gm]
    [cljs.reader :as reader]))

(defn init []
  (gm/init reader/read-string))

