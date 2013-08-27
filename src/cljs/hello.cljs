(ns wlmmap.hello
  (:require
   [shoreleave.remotes.http-rpc :refer [remote-callback]]))

(remote-callback :testremote [] #(js/alert %))
