(ns twitter-clj-app.core
  (:require [clojure.data.codec.base64 :as b64]
            [clj-http.client :as client]
            [oauth.signature :as oas]
            [environ.core :refer [env]]))

(defn env-or-throw
  "Loads `variable-name` from ENV or raises IllegalArgumentException."
  [variable-name]
  (or
    (env variable-name)
    (throw (IllegalArgumentException. (str variable-name " wasn't found in the environment")))))

(def skip-ssl? true)

(def api-url "https://api.twitter.com/1.1/")

(def api-endpoint (str api-url "statuses/user_timeline.json"))

(def token-endpoint "https://api.twitter.com/oauth2/token")

(def consumer-key (env-or-throw :twitter-api-key))

(def consumer-secret (env-or-throw :twitter-api-secret))

(defn- encode-app-only-key
  "Given a consumer-key and consumer-secret, concatenates and Base64
  encodes them so that they can be submitted to Twitter in exchange
  for an application-only token."
  [consumer-key consumer-secret]
  (let [concat-keys (str (oas/url-encode consumer-key) ":" (oas/url-encode consumer-secret))
        base64-bytes (b64/encode (.getBytes concat-keys))]
    (String. ^bytes base64-bytes "UTF-8")))

(defn get-token!
  []
  (let [auth-string (str "Basic " (encode-app-only-key consumer-key consumer-secret))
        content-type "application/x-www-form-urlencoded;charset=UTF-8"
        request (client/post token-endpoint
                             {:insecure skip-ssl?
                              :headers {"Authorization" auth-string
                                        "Content-Type" content-type}
                              :body "grant_type=client_credentials"
                              :as :json-strict
                              :redirect-strategy :none})]
    (get-in request [:body :access_token])))

(def app-only-token (future (get-token!)))

(defn call-twitter!
  []
  (client/get api-endpoint
              {:insecure? skip-ssl?
               :async false
               :as :json-strict
               :query-params {"screen_name" "ElenaSlavcheva"
                              "count" 2}
               :headers {"Authorization" (str "Bearer " @app-only-token)}}))

(defn -main
  "I don't do a whole lot ... yet."
  [& _args]
  (println ((call-twitter!) :body))
  (shutdown-agents))
