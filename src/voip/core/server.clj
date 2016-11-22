(ns voip.core.server
  (:require [aleph.tcp :as tcp]
            [clojure.core.match :refer [match]]
            [voip.core.util :as util]
            [voip.core.comms :as comms]
            [voip.core.state :as state]
            [manifold.stream :as s]))

(defn init [port]
  "Initialise the server kernel object"
  (atom {:kind         "server"
         :port         (read-string port)
         :peer-streams {}
         :state        (state/make)}))


(defn propogate-state
  "Propogate state to all peers"
  [server]
  (let [state (:state @server)]
    (doseq [[_ stream] (:peer-streams @server)]
      (util/write stream (comms/message 'state {:state state})))))


(defn update-state
  "Update the server state and propogate the results"
  [server updater]
  (swap! server
         (fn [current]
           (update current :state updater)))
  (propogate-state server))

(defn msg-handler [server stream msg]
  "Handle packets comming in from clients"
  (match (:type msg)

         'connect
         (let [peer (:peer msg)]
           ;Associate the stream to the peer
           (swap! server
                  (fn [current]
                    (update current :peer-streams #(assoc % peer stream))))

           (s/on-drained
             stream
             (fn []
               (update-state server (partial state/peer-disconnect peer))
               (swap! server (fn [current]
                               (update current :peer-streams #(dissoc % peer))))))

           ;Add the peer to the state
           (update-state server (partial state/peer-connect peer)))

         'create-channel
         (let [name (:name msg)]
           (update-state server (partial state/create-channel name)))

         'delete-channel
         (let [channel (:channel msg)]
           (update-state server (partial state/delete-channel channel)))

         'join-channel
         (let [channel (:channel msg)
               peer (:peer msg)]
           (update-state server (partial state/join-channel channel peer)))

         'leave-channel
         (let [channel (:channel msg)
               peer (:peer msg)]
           (update-state server (partial state/leave-channel channel peer)))

         'send-message
         (let [channel (:channel msg)
               message (:message msg)]
           (update-state server (partial state/send-message channel message)))

         'state
         (propogate-state server)

         :else true))

(defn prompt-handler [server input]
  "The server prompt"
  (match input
         "exit" false
         :else true))

(defn start! [this]
  ; Start the tcp server
  (let [tcp-server (tcp/start-server
                     (fn [stream info]
                       (let [msg-stream (util/wrap-duplex-stream stream)]
                         (s/consume
                           (partial msg-handler this msg-stream)
                           msg-stream)))
                     {:port (:port @this)})]

    ; Atomically add the tcp server
    (swap! this
           #(assoc %
             :tcp-server tcp-server)))


  ; Start the server prompt
  (util/prompt (partial prompt-handler this) "server >> "))

(defn stop! [this]
  "Stop the server services"
  (.close (:tcp-server @this)))



