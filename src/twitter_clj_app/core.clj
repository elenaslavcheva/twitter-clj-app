(ns twitter-clj-app.core
  (:require [clojure.data.codec.base64 :as b64]
            [clj-http.client :as client]
            [oauth.signature :as oas]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [clojure.string :as s])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn env-or-throw
  "Loads `variable-name` from ENV or raises IllegalArgumentException."
  [variable-name]
  (or
    (env variable-name)
    (throw (IllegalArgumentException. (str variable-name " wasn't found in the environment")))))

(def skip-ssl? true)

(def debug-http-requests? false)

(def api-url "https://api.twitter.com/1.1/")

(def timeline-endpoint "statuses/user_timeline.json")

(def media-upload-endpoint "https://upload.twitter.com/1.1/media/upload.json?")

(def api-endpoint (str api-url timeline-endpoint))

(def token-endpoint "https://api.twitter.com/oauth2/token")

(def consumer-key (env-or-throw :twitter-api-key))

(def consumer-secret (env-or-throw :twitter-api-secret))

(def test-png (io/file "resources/test.png"))

(defn- encode-app-only-key
  "Given a consumer-key and consumer-secret, concatenates and Base64
  encodes them so that they can be submitted to Twitter in exchange
  for an application-only token."
  [consumer-key consumer-secret]
  (let [concat-keys (str (oas/url-encode consumer-key) ":" (oas/url-encode consumer-secret))
        base64-bytes (b64/encode (.getBytes concat-keys))]
    (String. ^bytes base64-bytes "UTF-8")))


(def default-kwargs {:insecure skip-ssl?
                     :as :json-strict
                     :debug debug-http-requests?
                     :redirect-strategy :none})

(defn get-token!
  []
  (let [auth-string (str "Basic " (encode-app-only-key consumer-key consumer-secret))
        content-type "application/x-www-form-urlencoded;charset=UTF-8"
        request (client/post token-endpoint
                             (merge default-kwargs {:headers {"Authorization" auth-string
                                                              "Content-Type" content-type}
                                                    :body "grant_type=client_credentials"}))]
    (get-in request [:body :access_token])))

(def app-only-token (future (get-token!)))

(def default-headers {"Authorization" (str "Bearer " @app-only-token)})

(defn twitter-get
  "Makes a get request to `endpoint`."
  [endpoint & [keyword-arguments & rest]]
  (let [url (str api-url endpoint)
        headers (merge default-headers (:headers keyword-arguments))
        kwargs (merge default-kwargs keyword-arguments {:headers headers})]
    (try+
      (:body (apply client/get (concat [url kwargs] rest)))
      (catch [:status 401] e (println "Problem with authorization\n" e))
      (catch [:status 403] e (println "The action is forbidden\n" e))
      (catch [:status 404] e (println "The requested resource is missing\n" e))
      (catch [:status 500] e (println "The API responsed with 'Internal Server Error'" e) (throw+))
      (catch [:status 502] e (println "The API didn't respond in time" e) (throw+)))))

(defn http-function
  "Returns an appropriate for `method` function to execute a HTTP request."
  [method]
  ({:get client/get
    :post client/post} method))

(defn twitter-call
  "Make a `method` call to `endpoint`"
  [method endpoint & [keyword-arguments & rest]]
  (let [url (if (s/starts-with? endpoint "https://")
              endpoint
              (str api-url endpoint))
        headers (merge default-headers (:headers keyword-arguments))
        kwargs (merge default-kwargs keyword-arguments {:headers headers})]
    (try+
      (:body (apply (http-function method) (concat [url kwargs] rest)))
      (catch [:status 401] e (println "Problem with authorization\n" e))
      (catch [:status 403] e (println "The action is forbidden\n" e))
      (catch [:status 404] e (println "The requested resource is missing\n" e))
      (catch [:status 500] e (println "The API responsed with 'Internal Server Error'" e) (throw+))
      (catch [:status 502] e (println "The API didn't respond in time" e) (throw+)))))

(defn call-twitter!
  []
  (client/get api-endpoint
              {:insecure? skip-ssl?
               :debug true
               :as :json-strict
               :query-params {"screen_name" "NikolayKotsev1"
                              "count" 2}
               :headers {"Authorization" (str "Bearer " @app-only-token)}}))

(defn upload-media
  "Uploads `file` to twitter and returns the `:media-id` given by Twitter"
  [file]
  (twitter-call :post
                media-upload-endpoint
                {:multipart [{:name "Content/type" :content "image/png"}
                             {:name "media" :content file}]}))

(defn get-user-timeline
  "Return a map containing information about `screen-name`'s twitter timeline.'"
  ([screen-name]
   (twitter-call :get
                 timeline-endpoint
                 {:query-params {"screen_name" screen-name}}))
  ([screen-name query-params]
   (twitter-call :get
                 timeline-endpoint
                 (merge-with into query-params {:query-params {"screen_name" screen-name}}))))

(defn -main
  "I don't do a whole lot ... yet."
  [& _args]
  (println (get-user-timeline "NikolayKotsev1"))
  (shutdown-agents))
