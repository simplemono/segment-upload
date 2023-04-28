(ns simplemono.segment-upload.local
  (:require [ring.util.response :as response]
            [simplemono.segment-upload.util :as util]
            [clojure.java.io :as io]))

(def defaults
  {:segment-upload/local-dir "tmp/segment-upload"
   :segment-upload/local-url "/segment-upload/local-upload/"})

(defn put-upload-ring-handler
  [{:keys [ring/request
           ring/route-params
           segment-upload/local-dir]
    :as w}]
  (let [uuid-str (:uuid route-params)
        uuid (util/to-uuid uuid-str)
        dir (io/file local-dir
                     "segments")]
    (.mkdirs dir)
    (io/copy (:body request)
             (io/file dir
                      (str uuid)))
    (assoc w
           :ring/response
           (response/response ""))
    ))

(defn get-upload-url
  [{:keys [segment-upload/local-url
           segment-upload/uuid]}]
  (str local-url
       uuid))

(defn compose-files!
  [dest-file files]
  (with-open [out (io/output-stream (io/file dest-file))]
    (doseq [file files]
      (io/copy (io/file file)
               out))))

(defn compose
  [{:keys [segment-upload/uuids
           segment-upload/local-url
           segment-upload/local-dir]
    :as w}]
  (let [segments-dir (io/file local-dir
                              "segments")
        segment-files (map
                        (fn [uuid]
                          (io/file segments-dir
                                   (str uuid)))
                        uuids)
        dest-dir (io/file local-dir
                          "composed")
        uuid (java.util.UUID/randomUUID)
        dest-file (io/file dest-dir
                           (str uuid))]
    (.mkdirs dest-dir)
    (compose-files! dest-file
                    segment-files)
    (assoc w
           :segment-upload/compose-url
           (str local-url
                uuid))))

(defn get-composed-handler
  [{:keys [ring/route-params
           segment-upload/local-dir]
    :as w}]
  (let [uuid-str (:uuid route-params)
        uuid (util/to-uuid uuid-str)
        dir (io/file local-dir
                     "composed")]
    (when uuid
      (assoc w
             :ring/response
             (-> (io/file dir
                          (str uuid))
                 (response/response)
                 (response/content-type "application/octet-stream"))))
    ))

(defn add*
  [{:keys [segment-upload/local-url]
    :as w}]
  (-> w
      (assoc-in [:ring/routes
                 [:put (str local-url
                            ":uuid")]]
                #'put-upload-ring-handler)

      (assoc-in [:ring/routes
                 [:get (str local-url
                            ":uuid")]]
                #'get-composed-handler)

      (assoc :segment-upload/get-upload-url
             #'get-upload-url
             :segment-upload/compose
             #'compose)))

(defn add
  [{:keys [segment-upload/local-enabled]
    :as w}]
  (if local-enabled
    (add* (merge defaults
                 w))
    w))
