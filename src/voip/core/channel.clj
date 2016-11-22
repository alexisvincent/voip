(ns voip.core.channel)

(defn make
  "Create a channel"
  [name]
  {:name         name
   :members      #{}
   :call-members '()
   :messages     []})

(defn message
  "Create a channel message"
  [type message from]
  {:type    type
   :message message
   :from    from})

(defn voice-note
  "Create a voice message"
  [msg from]
  (message 'voice msg from))

(defn text-message
  "Create a text message"
  [msg from]
  (message 'text msg from))

(defn is-member?
  "Check if peer is member of channel"
  [channel peer]
  (some #(= % peer) (:members channel)))
