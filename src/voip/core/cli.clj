(ns voip.core.cli
  (:require [voip.core.kernel :as kernel])
  (:gen-class))

(defn -main [& args]
  (doto (apply kernel/init args)
    (kernel/start)
    (kernel/stop))
  (System/exit 0))
