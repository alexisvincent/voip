(ns dev
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
    [clojure.java.javadoc :refer [javadoc]]
    [clojure.pprint :refer [pprint]]
    [clojure.reflect :refer [reflect]]
    [clojure.repl :refer [apropos dir doc find-doc pst source]]
    [clojure.tools.namespace.repl :refer [refresh refresh-all]]
    [voip.core.kernel :as kernel]
    [voip.core.util :as util]))


(def server-args ["server" "8000"])
(def client-args ["client" "localhost" "8000" "hostname"])

(def args [])

(def repl-instance nil)

(defn init
  "Creates and initializes the system under development in the Var
  #'system."
  []
  (alter-var-root #'repl-instance (constantly
                                    (do
                                      (util/prompt
                                        #(do
                                          (alter-var-root
                                            #'args
                                            (constantly (if (or (= % "s") (= % "")) server-args client-args)))
                                          false)
                                        "client [hostname] or server [s]: (s) ")
                                      (apply kernel/init args)))))

(defn start
  "Starts the system running, updates the Var #'system."
  []
  (kernel/start repl-instance))

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system."
  []
  (try
    (kernel/stop repl-instance)
    (catch Exception e))
  (alter-var-root #'repl-instance (constantly nil)))

(defn go
  "Initializes and starts the system running."
  []
  (init)
  (start))

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (if (not (nil? repl-instance)) (stop))
  (refresh :after `go))