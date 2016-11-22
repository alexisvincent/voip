(ns voip.core.comms)

(defn message
  "Create an coordination message"
  [type msg]
  (assoc msg :type type))

