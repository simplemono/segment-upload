(ns simplemono.segment-upload.core
  (:require [simplemono.segment-upload.util :as util]
            [simplemono.world.core :as w]
            ))

(defn get-upload-ring-handler
  [{:keys [ring/route-params segment-upload/get-upload-url] :as w}]
  (when-let [uuid (util/to-uuid (:uuid route-params))]
    (let [upload-url (get-upload-url (assoc w
                                            :segment-upload/uuid
                                            uuid))]
      (assoc w
             :ring/response
             (util/json-response {:url (str upload-url)}))
      )))

(defn check-segment-count
  [{:keys [api/params segment-upload/max-compose-segments] :as w
    :or {max-compose-segments 1000}}]
  (if (< max-compose-segments
         (count (:uuids params)))
    (util/throw-error
     {:error-message (str "It is not allowed to compose more than "
                          max-compose-segments
                          " segments.")})
    w))

(defn extract-compose-params
  [{:keys [api/params] :as w}]
  (assoc w
         :segment-upload/uuids (map util/to-uuid
                                    (:uuids params))))

(defn validate-segment-uuids
  [{:keys [segment-upload/uuids] :as w}]
  (cond
    (empty? uuids)
    (util/throw-error
     {:error-message "Please provide at least one UUID."})

    (some nil?
          uuids)
    (util/throw-error "Please only provide UUIDs.")

    :else
    w))

(defn invoke-compose
  [{:keys [segment-upload/compose] :as w}]
  (compose w))

(defn add-api-result
  [{:keys [segment-upload/compose-url] :as w}]
  (assoc w
         :api/result
         {:url compose-url}))

(defn compose-segments-ring-handler
  [w]
  (w/w<
    (util/add-json-ring-response
      (add-api-result
        (invoke-compose
          (validate-segment-uuids
            (extract-compose-params
              (check-segment-count
                (util/parse-json-api-params w)))))))))

(defn add
  [w]
  (-> w
      (assoc-in [:ring/routes
                 [:get "/segment-upload/upload/:uuid"]]
                #'get-upload-ring-handler)
      (assoc-in [:ring/routes
                 [:post "/segment-upload/compose"]]
                #'compose-segments-ring-handler)))

(comment
  (require '[clj-http.client :as http])

  ;; Example of a basic upload client implementation:

  (defn get-upload-url
    "Gets a signed upload URL from the server."
    [{:keys [base-url uuid]}]
    (-> {:request-method :get
         :url (str base-url
                   "/segment-upload/upload/"
                   uuid)
         :as :json}
        (http/request)
        (get-in [:body :url])))

  (get-upload-url
    {:base-url "http://localhost:8080"
     :uuid (random-uuid)})

  (defn upload-segment!
    "Uploads the `:content` as segment."
    [{:keys [uuid content] :as params}]
    (let [upload-url (get-upload-url params)]
      (http/request
        {:request-method :put
         :url upload-url
         :headers {"Content-Type" "text/plain"}
         :body content})
      {:uuid uuid}))

  (def content
    ;; Example content to upload:
    "hello upload")

  (def example-uuids
    ;; UUIDs for the segments. Just for demonstration purposes we upload each
    ;; letter as separate segment:
    (repeatedly (count content)
                (fn []
                  (java.util.UUID/randomUUID))))

  ;; Uploads all segments:
  (doseq [[letter uuid] (map vector
                             content
                             example-uuids)]
    (upload-segment! {:base-url "http://localhost:8080"
                      :uuid uuid
                      :content (str letter)}))

  (def composed-url
    ;; Composes all segments into a single file on the server that can be
    ;; downloaded via the `composed-url`:
    (-> {:request-method :post
         :url "http://localhost:8080/segment-upload/compose"
         :content-type :json
         :form-params {:uuids example-uuids}
         :as :json
         }
        (http/request)
        (get-in [:body :url])))

  ;; The composed file should have the uploaded `content`:
  (= content
     (-> composed-url
         (http/get)
         (:body))
     )

  )
