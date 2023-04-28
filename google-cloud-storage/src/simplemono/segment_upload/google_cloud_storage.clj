(ns simplemono.segment-upload.google-cloud-storage
  (:require [simplemono.world.core :as w])
  (:import [com.google.cloud.storage
            HttpMethod
            BlobId
            BlobInfo
            Storage$SignUrlOption
            Storage$ComposeRequest
            Storage$BlobWriteOption]))

(defn get-storage
  []
  (-> (com.google.cloud.storage.StorageOptions/newBuilder)
      (.build)
      (.getService)))

(defn create-blob-info
  [{:keys [bucket content-type path]}]
  (-> (BlobInfo/newBuilder bucket path)
      (.setContentType content-type)
      (.build)))

(defn sign-put-url
  [{:keys [credentials storage content-type] :as params}]
  (let [blob-info (create-blob-info params)]
    (.signUrl
      storage
      blob-info
      1
      java.util.concurrent.TimeUnit/DAYS
      (into-array
        (cond-> [(Storage$SignUrlOption/httpMethod HttpMethod/PUT)
                 (Storage$SignUrlOption/signWith credentials)]
          content-type
          (conj (Storage$SignUrlOption/withContentType)))))))

(defn add-sign-url
  [{:keys [::sign-url-params] :as w}]
  (assoc w
         ::signed-url
         (sign-put-url sign-url-params)))

(defn prepare-sign-url
  [{:keys [segment-upload/uuid
           segment-upload/google-cloud-bucket
           google-cloud/get-credentials
           ring/request] :as w}]
  (assoc w
         ::sign-url-params
         {:storage (get-storage)
          :credentials (get-credentials)
          :bucket google-cloud-bucket
          :path (str "segments/"
                     uuid)
          :content-type (get-in request
                                [:headers
                                 "content-type"])}))

(defn get-upload-url
  [w]
  (::signed-url
   (w/w< (add-sign-url
           (prepare-sign-url w)))))

(defn compose-request
  [{:keys [bucket target sources]}]
  (-> (Storage$ComposeRequest/newBuilder)
      (.addSource sources)
      (.setTarget (-> (BlobInfo/newBuilder
                        bucket
                        target)
                      (.build)))
      (.build)))

(def maximum-compose-request-sources
  32)

(defn compose-plan
  [{:keys [max-items prefix paths]}]
  (loop [plan []
         paths paths]
    (let [current (str prefix
                       (java.util.UUID/randomUUID))
          plan* (conj plan
                      [current
                       (take max-items
                             paths)])
          paths* (drop max-items
                       paths)]
      (if (seq paths*)
        (recur plan*
               (cons current
                     paths*))
        plan*)
      )))

(defn compose!
  [{:keys [storage] :as params}]
  (let [plan (compose-plan params)]
    (doseq [[target sources] plan]
      (.compose storage
                (compose-request
                  (assoc params
                         :target target
                         :sources sources))))
    {:path (first (last plan))}))

(comment
  (compose-plan
    {:max-items 4
     :prefix "composed/"
     :paths (map str
                 (range 20))})

  (let [storage (get-storage)]
    (doseq [n (range 200)]
      (.createFrom storage
                   (-> (BlobId/of "your-example-bucket"
                                  (str "demo/"
                                       n))
                       (BlobInfo/newBuilder)
                       (.build))
                   (java.io.ByteArrayInputStream. (.getBytes (str n
                                                                  "\n")
                                                             "UTF-8"))
                   (into-array Storage$BlobWriteOption
                               []))))

  (:path
   (time
     (compose! {:bucket "your-example-bucket"
                :storage (get-storage)
                :max-items maximum-compose-request-sources
                :prefix "composed/"
                :paths (map (fn [n]
                              (str "demo/"
                                   n))
                            (range 200))})))
  )

(defn prepare-compose
  [{:keys [segment-upload/uuids
           segment-upload/google-cloud-bucket]
    :as w}]
  (assoc w
         ::compose-params
         {:bucket google-cloud-bucket
          :storage (get-storage)
          :max-items maximum-compose-request-sources
          :prefix "composed/"
          :paths (map (fn [uuid]
                        (str "segments/"
                             uuid))
                      uuids)}))

(defn invoke-compose
  [{:keys [::compose-params] :as w}]
  (assoc w
         ::compose-path
         (:path (compose! compose-params))))

(defn prepare-sign-get-url
  [{:keys [segment-upload/google-cloud-bucket
           google-cloud/get-credentials
           ::compose-path]
    :as w}]
  (assoc w
         ::sign-compose-url-params
         {:storage (get-storage)
          :credentials (get-credentials)
          :bucket google-cloud-bucket
          :path compose-path}))

(defn sign-get-url
  [{:keys [credentials storage] :as params}]
  (let [blob-info (create-blob-info params)]
    (.signUrl
      storage
      blob-info
      10
      java.util.concurrent.TimeUnit/MINUTES
      (into-array [(Storage$SignUrlOption/httpMethod HttpMethod/GET)
                   (Storage$SignUrlOption/signWith credentials)]))))

(defn add-signed-get-url
  [{:keys [::sign-compose-url-params] :as w}]
  (assoc w
         :segment-upload/compose-url
         (str
           (sign-get-url sign-compose-url-params))))

(defn segment-upload-compose
  [w]
  (w/w<
    (add-signed-get-url
      (prepare-sign-get-url
        (invoke-compose
          (prepare-compose w))))))

(defn add
  [{:keys [segment-upload/google-cloud-bucket] :as w}]
  (if google-cloud-bucket
    (-> w
        (assoc :segment-upload/get-upload-url
               #'get-upload-url)
        (assoc :segment-upload/compose
               #'segment-upload-compose))
    w))
