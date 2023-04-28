(ns simplemono.segment-upload.core
  (:require [ring.util.response :as response]
            [simplemono.segment-upload.util :as util]
            [simplemono.world.core :as w]
            ))

(defn put-upload-ring-handler
  [{:keys [ring/route-params segment-upload/get-upload-url] :as w}]
  (when-let [uuid (util/to-uuid (:uuid route-params))]
    (let [upload-url (get-upload-url (assoc w
                                            :segment-upload/uuid
                                            uuid))]
      (assoc w
             :ring/response
             (response/redirect upload-url
                                307))
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
                 [:put "/segment-upload/upload/:uuid"]]
                #'put-upload-ring-handler)
      (assoc-in [:ring/routes
                 [:post "/segment-upload/compose"]]
                #'compose-segments-ring-handler)))

(comment
  (require '[clj-http.client :as http]
           '[clojure.string :as str])

  (def example-uuid
    (java.util.UUID/randomUUID))

  (def example-request
    {:request-method :put
     :url (str "http://localhost:8080/segment-upload/upload/"
               example-uuid)
     :body "hello"})

  (let [location (get-in (http/request example-request)
                         [:headers
                          "Location"])]
    (http/request
      (assoc example-request
             :url
             (if (str/starts-with? location
                                   "/")
               (str "http://localhost:8080"
                    location)
               location))))

  (defn upload-segment!
    [{:keys [base-url uuid content]}]
    (let [request {:request-method :put
                   :url (str base-url
                             "/segment-upload/upload/"
                             uuid)
                   :headers {"Content-Type" "text/plain"}
                   :body content}
          location (get-in (http/request request)
                           [:headers
                            "Location"])]
      (http/request
        (assoc request
               :url
               (if (str/starts-with? location
                                     "/")
                 (str base-url
                      location)
                 location)))
      {:uuid uuid}))

  (def example-uuids
    (repeatedly 10
                (fn []
                  (java.util.UUID/randomUUID))))

  (doseq [[n uuid] (map-indexed vector
                                example-uuids)]
    (upload-segment! {:base-url "http://localhost:8080"
                      :uuid uuid
                      :content (str (inc n)
                                    "\n")}))

  (http/request
    {:request-method :post
     :url "http://localhost:8080/segment-upload/compose"
     :content-type :json
     :form-params {:uuids example-uuids}
     })

  ;; JS example:
  ;; fetch('/segment-upload/upload/' + crypto.randomUUID(), {method: 'PUT', body: 'hello from js'})
  )
