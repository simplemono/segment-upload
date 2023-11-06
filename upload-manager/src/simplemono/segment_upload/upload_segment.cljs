(ns simplemono.segment-upload.upload-segment)

(defn add-error-event-handler!
  [{:keys [a xhr]}]
  (.addEventListener xhr
                     "error"
                     (fn [e]
                       (a (fn [state]
                            (assoc state
                                   :error :error-event
                                   :error-event e
                                   :error-date (js/Date.)
                                   ))))))

(defn add-on-ready-state-change!
  [{:keys [a xhr success-callback]}]
  (set! (.-onreadystatechange xhr)
        (fn []
          (when (= (.-readyState xhr)
                   4)
            (case (.-status xhr)
              0
              (a (fn [state]
                   (assoc state
                          :error :offline
                          :error-date (js/Date.))))
              200
              (success-callback)

              ;; default:
              (a (fn [state]
                   (assoc state
                          :error :unknown
                          :error-date (js/Date.)
                          :error-response {:status (.-status xhr)
                                           :body (.-response xhr)})))
              )))))

(defn put-request!
  [a]
  (let [{:keys [segment-blob upload-url]} (a)
        xhr (js/XMLHttpRequest.)
        upload (.-upload xhr)]
    (a (fn [state]
         (-> state
             (assoc :abort (fn []
                             (.abort xhr)))
             (dissoc :error
                     :error-event
                     :error-response
                     :error-date))))
    (.addEventListener upload
                       "progress"
                       (fn [e]
                         (when (.-lengthComputable e)
                           (a (fn [state]
                                (let [progress (/ (.-loaded e)
                                                  (.-size segment-blob))]
                                  (assoc state
                                         :progress progress)))))))
    (add-error-event-handler! {:a a
                               :xhr xhr})
    (add-on-ready-state-change!
      {:a a
       :xhr xhr
       :success-callback (fn []
                           (a (fn [state]
                                (assoc state
                                       :done true))))})
    (set! (.-responseType xhr)
          "text")
    (.open xhr
           "PUT"
           upload-url)
    (.send xhr
           segment-blob)
    ))

(defn get-upload-url!
  [a]
  (let [{:keys [segment-uuid segment-upload-url]} (a)
        xhr (js/XMLHttpRequest.)]
    (add-error-event-handler!
      {:a a
       :xhr xhr})
    (add-on-ready-state-change!
      {:a a
       :xhr xhr
       :success-callback (fn []
                           (a (fn [state]
                                (assoc state
                                       :upload-url (aget (.-response xhr)
                                                         "url"))))
                           (put-request! a))})
    (set! (.-responseType xhr)
          "json")
    (.open xhr
           "GET"
           (str segment-upload-url
                segment-uuid))
    (.send xhr
           nil)
    ))

(defn upload-segment!
  [a]
  (a (fn [state]
       (assoc state
              :started true)))
  (get-upload-url! a)
  )

(comment
  (do
    (def state
      (atom
        {:segment-uuid (str (random-uuid))
         :segment-blob (js/Blob. ["hello"]
                                 #js {:type "text/plain"}
                                 )
         :segment-upload-url "/segment-upload/upload/"}))

    @state

    (def a
      (fn
        ([] @state)
        ([swap-fn] (swap! state
                          swap-fn))))

    (a)

    (upload-segment! a)
    )
  )
