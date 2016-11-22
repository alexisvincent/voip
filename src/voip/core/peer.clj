(ns voip.core.peer)

(defn make
  "Create a peer"
  [hostname ip port]
  {:hostname hostname
   :ip       ip
   :port     port})


