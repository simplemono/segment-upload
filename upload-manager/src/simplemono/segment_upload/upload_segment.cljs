(ns simplemono.segment-upload.upload-segment)

(defn upload-segment!
  [a]
  (let [{:keys [segment-uuid segment-blob segment-upload-url]} (a)
        xhr (js/XMLHttpRequest.)
        upload (.-upload xhr)]
    (a (fn [state]
         (-> state
             (assoc :started true
                    :abort (fn []
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
    (.addEventListener xhr
                       "error"
                       (fn [e]
                         (a (fn [state]
                              (assoc state
                                     :error :error-event
                                     :error-event e
                                     :error-date (js/Date.)
                                     )))))
    (set! (.-onreadystatechange xhr)
          (fn []
            (when (= (.-readyState xhr)
                     4)
              (case (.-status xhr)
                0
                (a (fn [state]
                     (assoc state
                            :error :offline)))
                200
                (a (fn [state]
                     (assoc state
                            :done true)))

                ;; default:
                (a (fn [state]
                     (assoc state
                            :error :unknown
                            :error-response {:status (.-status xhr)
                                             :body (.-responseText xhr)})))
                ))))
    (set! (.-responseType xhr)
          "text")
    (.open xhr
           "PUT"
           (str segment-upload-url
                segment-uuid))
    (.send xhr
           segment-blob)
    ))

(comment
  (require 'simplemono.segment-upload.util)

  (do
    (def state
      (atom
        {:segment-uuid (simplemono.segment-upload.util/random-uuid)
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
