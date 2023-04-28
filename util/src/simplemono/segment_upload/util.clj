(ns simplemono.segment-upload.util
  (:require [cheshire.core :as json]
            [ring.util.response :as response]))

(defn to-uuid
  [uuid-str]
  (try
    (java.util.UUID/fromString uuid-str)
    (catch Exception _e
      nil)))

(defn json-response
  "Returns a Ring response with a the `data` encoded as JSON."
  [data]
  (-> data
      (json/generate-string)
      (response/response)
      (response/content-type "application/json")))

(defn throw-error
  "Throws an error that results in a HTTP 400 response for the API consumer.
   Provide a `error-message` that will be included as `:errorMessage` in the JSON
   response body."
  [{:keys [error-message]}]
  (throw (ex-info
           error-message
           {:ring/response (-> {:errorMessage error-message}
                               (json-response)
                               (assoc :status 400))})))

(defn parse-json-api-params
  "Parses the `:body` of the `:ring/request` as JSON and adds it to the world as
   `:api/params`.

   Use `:ring/max-body-bytes` to define the maximum number of bytes allowed
   inside the Ring request `:body` (default: 50 MiB)"
  [{:keys [ring/request] :as w}]
  (let [default-max-body-bytes (* 1024 1024 50)
        max-body-bytes (or (:ring/max-body-bytes w)
                           default-max-body-bytes)
        body (:body request)
        body* (if (instance? java.io.InputStream
                             body)
                (slurp
                  (com.google.common.io.ByteStreams/limit body
                                                          max-body-bytes))
                body)]
    (assoc w
           :api/params
           (try
             (json/parse-string body*
                                true)
             (catch com.fasterxml.jackson.core.JsonParseException _
               (throw-error
                 {:error-message "Request body contains invalid JSON"}))))))

(defn add-json-ring-response
  "Assumes that the result of the invoked API procedure resides under the
   `:api/result` entry in the world `w`. Adds a `:ring/response` to the world
   with the data encoded as JSON."
  [{:keys [api/result] :as w}]
  (assoc w
         :ring/response (json-response result)))
