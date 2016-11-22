(ns voip.core.state
  (:require [voip.core.channel :as channel]))

(defn make
  "Create the state structure"
  []
  {:peers    #{}
   :channels {}})

(defn get-channel
  "Extract channel by name"
  [state name]
  (get-in state [:channels name]))

(defn get-connected-channel
  "Get the channel currently connected to"
  [state peer]
  (let [[_ chan] (first
                   (filter
                     (fn [[_ channel]]
                       (get-in channel [:members peer]))
                     (:channels state)))]
    chan))

(defn peer-connect
  "Add a peer to the connected list"
  [peer state]
  (if (not (some #(= (:hostname peer) (:name %)) (:peers state)))
    (update state :peers #(conj % peer))
    state))

(defn peer-disconnect
  "Remove a peer from the connected list"
  [peer state]
  (update state :peers
          (fn [peers]
            (remove #(= % peer) peers))))

(defn create-channel
  "Create a new channel"
  [name state]
  (if (not (get-in state [:channels name]))
    (update state :channels #(assoc % name (channel/make name)))
    state))

(defn delete-channel
  "Delete a channel"
  [{name :name} state]
  (if (< (count (:members (get-channel state name))) 2)
    (update state :channels #(dissoc % name))
    state))

(defn join-channel
  "Join a channel"
  [{name :name} peer state]

  (let [channel (get-channel state name)]
    (if channel
      (update-in state [:channels name :members] #(set (conj % peer)))
      state)))

(defn leave-channel
  "Leave a channel"
  [{name :name} peer state]
  (update-in state [:channels name :members]
             (fn [members]
               (remove #(= peer %) members))))

(defn send-message
  "Join a channel"
  [{name :name} msg state]
  (update-in state [:channels name :messages]
             #(conj % (assoc msg :name (rand-int 100)))))

(defn peers
  "Get online peers"
  [state]
  (get state :peers))

(defn channels
  "Get channels"
  [state]
  (get state :channels))


