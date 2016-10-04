(ns voip.core.client
  (:require [manifold.stream :as s]
            aleph.tcp
            [voip.core.util :as util]
            [clojure.core.match :refer [match]]))



(defn init [server-ip server-port]
  (println "client")
  (atom {:kind        "client"
         :server/ip   server-ip
         :server/port (read-string server-port)}))



(defn write [client msg]
  (util/write (:stream @client) msg))



(defn worker [client delay]
  (future
    (while (not (Thread/interrupted))
      (Thread/sleep delay))))




(defn msg-handler [client stream msg]
  (match (:label msg)
         :else (println "Unmatched " msg)))




(defn make-stream [client server-ip server-port]
  "Create a new "
  (let [stream @(aleph.tcp/client {:host server-ip :port server-port})]
    (util/consume-edn-stream stream (partial msg-handler client stream))
    stream))




(defn prompt [client input]
  "Prompt for and handle user input"
  (match input
         "exit" false
         "quit" false
         :else true))




(defn start [this]
  ; Create stream to server and construct a function to write from the stream
  (let [stream (make-stream this (:server/ip @this) (:server/port @this))
        worker (worker this 10000)]

    ;; Assign to atom the stream and write function
    (swap! this #(assoc %
                  :stream stream
                  :worker worker)))

  ;; Prompt user for input
  (util/prompt (partial prompt this) "client >> "))



(defn stop [this]
  (future-cancel (:worker @this))
  (s/close! (:stream @this)))


