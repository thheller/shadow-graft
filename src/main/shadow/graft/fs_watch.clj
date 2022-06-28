(ns shadow.graft.fs-watch
  (:require
    [shadow.graft :as graft]
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(defn read-manifest [file]
  (edn/read-string (slurp file)))

(defn start [svc path-to-graft-edn]
  (let [keep-watching-ref
        (atom true)

        manifest-file
        (io/file path-to-graft-edn)

        _
        (graft/set-manifest svc
          (read-manifest manifest-file))

        thread
        (doto (Thread.)
          (fn []
            (loop [last-mod (.lastModified manifest-file)]
              (try
                (Thread/sleep 500)
                (catch InterruptedException _
                  ;; assumes the keep-watching-ref is now false and the thread will exit
                  ))

              (when @keep-watching-ref
                (let [new-mod (.lastModified manifest-file)]
                  (if (= last-mod new-mod)
                    (recur last-mod)
                    (let [new-manifest (read-manifest manifest-file)]
                      (graft/set-manifest svc new-manifest)
                      (recur new-mod)))))))

          (.start))]

    {:path-to-graft-edn path-to-graft-edn
     :keep-watching-ref keep-watching-ref
     :thread thread
     }))

(defn stop [{:keys [keep-watching-ref ^Thread thread] :as svc}]
  (reset! keep-watching-ref false)
  (.interrupt thread))