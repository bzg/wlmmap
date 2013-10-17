(ns immutant.init
  (:use wlmmap.handler)
  (:require [immutant.web :as web]))

(web/start #'ring-handler)
