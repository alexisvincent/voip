(ns voip.core.server
  (:require [aleph.tcp :as tcp]
            [clojure.core.match :refer [match]]
            [voip.core.util :as util]))

(defn init [port]
  "Initialise the server kernel object"
  (println "client")
  (atom {:kind "server"
         :port (read-string port)}))


(defn worker [server delay]
  (future
    (while (not (Thread/interrupted))
      (Thread/sleep delay)
      (when (not (Thread/interrupted))))))

(defn msg-handler [server stream msg]
  "Handle packets comming in from clients"
  (match (:label msg)
         'heartbeat
         (do)))


(defn prompt-handler [server input]
  "The server prompt"
  (match input
         "exit" false
         :else true))

(defn start [this]
  ; Start the tcp server
  (let [tcp-server (tcp/start-server
                     (fn [stream info]
                       (util/consume-edn-stream
                         stream
                         (partial msg-handler this stream)))
                     {:port (:port @this)})

        worker (worker this 1000)]

    ; Atomically add the tcp server
    (swap! this
           #(assoc %
             :worker worker
             :tcp-server tcp-server)))

  ; Start the server prompt
  (util/prompt (partial prompt-handler this) "server >> "))

(defn stop [this]
  "Stop the server services"
  (future-cancel (:worker @this))
  (.close (:tcp-server @this)))



