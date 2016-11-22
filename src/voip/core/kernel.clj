(ns voip.core.kernel
  (:require [voip.core.client :as client]
            [voip.core.server :as server]))

(defn isClient? [kernel] (= (:kind @kernel) "client"))
(defn isServer? [kernel] (not (isClient? kernel)))

(defn init
  "Returns a new instance of the application. (an atom)"
  [kind & args]
  (if (= kind "client")
    (apply client/init args)
    (apply server/init args)))

(defn start [instance]
  (if (isClient? instance)
    (client/start! instance)
    (server/start! instance)))

(defn stop [instance]
  (if (isClient? instance)
    (client/stop! instance)
    (server/stop! instance)))
