(ns twitter-clj-app.core
  (:require [clojure.data.codec.base64 :as b64]
            [clj-http.client :as client]
            [oauth.signature :as oas]))

(def api-endpoint
  "https://api.twitter.com/1.1/statuses/user_timeline.json?screen_name=ElenaSlavcheva&count=2")

(def token-endpoint
  "https://api.twitter.com/oauth2/token")

(def consumer-key
  "consumer-key")

(def consumer-secret
  "consumer-secret")

(def app-only-token
  "token")

(defn- encode-app-only-key
  "Given a consumer-key and consumer-secret, concatenates and Base64
  encodes them so that they can be submitted to Twitter in exchange
  for an application-only token."
  [consumer-key consumer-secret]
  (let [concat-keys (str (oas/url-encode consumer-key) ":" (oas/url-encode consumer-secret))
        base64-bytes (b64/encode (.getBytes concat-keys))]
    (String. ^bytes base64-bytes "UTF-8")))

(defn call-twitter!
  []
  (client/get api-endpoint
              {:async? true
               :headers {"Authorization" (str "Bearer " app-only-token)}}
            ;; respond callback
              (fn [response] (println "response is:" response))
            ;; raise callback
              (fn [exception] (println "exception message is: " (.toString exception)))))

(defn get-token!
  []
  (let [auth-string (str "Basic " (encode-app-only-key consumer-key consumer-secret))
        content-type "application/x-www-form-urlencoded;charset=UTF-8"]
    (client/post token-endpoint
                {:async? true
                 :headers {"Authorization" auth-string
                           "Content-Type" content-type}
                 :body "grant_type=client_credentials"
                 :redirect-strategy :none}
            ;; respond callback
                (fn [response] (println "response is:" response))
            ;; raise callback
                (fn [exception] (println "exception message is: " (.toString exception))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& _args]
  (println "try getting token"))