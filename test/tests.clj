(ns tests
  (:use [wlmmap.handler])
  (:use [clojure.test])
  (:use [midje.sweet]))

(deftest db-options-is-not-empty
  (is (seq? @db-options)))

(fact (seq? @db-options) => true)
