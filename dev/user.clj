(ns user
  (:require [dev :as dev]
            [clojure.spec.test :as s]))

(defn start
  "Load and switch to the 'dev' namespace."
  []
  ;(require 'dev)
  (in-ns 'dev)
  (s/instrument))

(start)