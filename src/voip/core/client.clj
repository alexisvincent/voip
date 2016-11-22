(ns voip.core.client
  (:require [manifold.stream :as s]
            [aleph.udp :as udp]
            [aleph.tcp :as tcp]
            [voip.core.util :as util]
            [voip.core.comms :as comms]
            [voip.core.state :as state]
            [voip.core.peer :as peer]
            [voip.core.channel :as channel]
            [clojure.core.match :refer [match]]
            [clojure.string :as str]
            [voip.core.audio :as audio])
  (:import (java.net InetAddress)))



(defn init [server-ip server-port port hostname]
  (let [ip (.getHostAddress (InetAddress/getLocalHost))]
    (atom {:kind        "client"
           :server-ip   server-ip
           :server-port (read-string server-port)
           :peer        (peer/make hostname ip (read-string port))
           :state       (state/make)})))

(defn write! [client msg]
  (try
    (util/write (:server-stream @client) msg)
    (catch Exception e)))



(defn update-state
  "Update the local state and handle relevant changes"
  [client state]
  (swap! client #(assoc % :state state)))



(defn msg-handler [client stream msg]
  (match (:type msg)
         'state
         (update-state client (:state msg))

         :else (println "Unmatched " msg)))


(defn server-connection! [client server-ip server-port]
  "Create a new "
  (let [stream @(tcp/client {:host server-ip :port server-port})
        msg-stream (util/wrap-duplex-stream stream)]
    (s/consume (partial msg-handler client msg-stream) msg-stream)
    msg-stream))


(defn create-channel-req!
  "Request that a channel be created"
  [client name]
  (write! client (comms/message 'create-channel {:name name})))

(defn delete-channel-req!
  "Request that a channel be deleted"
  [client channel]
  (write! client (comms/message 'delete-channel {:channel channel})))

(defn join-channel-req!
  "Request to join a channel"
  [client channel peer]
  (write! client (comms/message 'join-channel {:channel channel :peer peer})))

(defn leave-channel-req!
  "Request to leave a channel"
  [client channel peer]
  (write! client (comms/message 'leave-channel {:channel channel :peer peer})))

(defn send-message-req!
  "Request to send message to channel"
  [client channel message]
  (write! client (comms/message 'send-message {:channel channel :message message})))



(defn state-req!
  "Request updated state"
  [client]
  (write! client (comms/message 'state {})))

(defn prompt [client input]
  "Prompt for and handle user input"
  (match (str/split input #" ")
         ["exit"]
         false

         ["peers"]
         (do
           (println "\nClients:\n")
           (doseq [client (state/peers (:state @client))]
             (println " [" (:hostname client) "] "))
           (println)
           true)

         ["channels"]
         (do
           (println "\nChannels:\n")
           (doseq [[name _] (state/channels (:state @client))]
             (println " [" name "] "))
           (println)
           true)

         ["state"]
         (do
           (println (:state @client))
           true)

         ["create" name]
         (create-channel-req! client name)

         ["join" name]
         (do
           (join-channel-req! client (state/get-channel (:state @client) name) (:peer @client))
           (Thread/sleep 1000)
           (let [peer (:peer @client)
                 channel (state/get-connected-channel (:state @client) peer)
                 name (:name channel)]
             (when (not (nil? channel))
               (util/prompt
                 (fn [input]
                   (match (str/split input #" ")

                          ["delete"]
                          (do
                            (delete-channel-req! client channel)
                            (Thread/sleep 1000)
                            (state/get-channel (:state @client) (:name channel)))

                          ["exit"]
                          (do
                            (leave-channel-req! client channel peer)
                            false)

                          ["text" & rest]
                          (let [msg (str/join " " rest)]
                            (send-message-req! client channel (channel/text-message msg peer)))

                          ["voice"]
                          (let [{recording :stream stop :stop} (audio/capture 10 1000)]
                            (read-line)
                            (stop)
                            (send-message-req! client channel
                                               (channel/voice-note
                                                 (s/stream->seq recording)
                                                 peer)))

                          ["play" & rest]
                          (let [id (first rest)
                                messages (get-in @client [:state :channels name :messages])
                                vn-msg (some #(if (and (= (:type %) 'voice) (= (read-string id) (:name %))) % nil) messages)]
                            (when vn-msg
                              (audio/play-stream (s/->source (:message vn-msg))))
                            true)


                          ["messages"]
                          (do
                            (println "\nMessages:\n")
                            (doseq [msg (get-in @client [:state :channels name :messages])]
                              (let [{type :type from :from message :message name :name} msg
                                    hostname (:hostname from)]


                                (if (= type 'text)
                                  (println hostname ":" message)
                                  (println hostname ":" name))))
                            (println)
                            true)


                          ["call" frame-size]
                          (do
                            (Thread/sleep 1000)
                            (let [channel (state/get-connected-channel (:state @client) peer)
                                  members (get channel :members)]

                              (when (not (nil? (get members peer)))
                                (let [[call-stream-stream microphone-stream] (util/wrap-voice-stream
                                                                               (:voice-stream @client)
                                                                               members
                                                                               peer)

                                      {voice-capture-stream :stream stop-microphone :stop} (audio/capture (read-string frame-size) 100)]

                                  (s/connect voice-capture-stream microphone-stream)

                                  (let [stop-playback (audio/play-streams2 call-stream-stream)]
                                    (read-line)
                                    (stop-microphone)
                                    (s/close! call-stream-stream)
                                    (stop-playback)))))

                            true)

                          :else
                          (do
                            (state-req! client)
                            true)))

                 (str/join (list "channel [" name "] > ")))))
           true)


         :else
         (do
           (state-req! client)
           true)))


(defn start! [this]
  ; Create stream to server and construct a function to write from the stream
  (let [server-stream (server-connection! this (:server-ip @this) (:server-port @this))
        peer (:peer @this)
        voice-stream @(udp/socket {:port (:port peer)})]

    ;; Assign to atom the stream and write function
    (swap! this #(assoc %
                  :server-stream server-stream
                  :voice-stream voice-stream))

    ; Connect to server
    (write! this (comms/message 'connect {:peer peer}))


    ;; Prompt user for input
    (Thread/sleep 1000)
    (when (some #(= peer %) (get-in @this [:state :peers]))
      (create-channel-req! this "default")
      (util/prompt (partial prompt this) "client >> "))))


(defn stop! [this]
  (s/close! (:server-stream @this))
  (s/close! (:voice-stream @this)))


