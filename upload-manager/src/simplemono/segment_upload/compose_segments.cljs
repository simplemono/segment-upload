(ns simplemono.segment-upload.compose-segments
  (:require [app.segment-upload.upload-segments :as upload-segments]))

(defn fetch
  [request]
  (js/fetch
    (:url request)
    (clj->js (dissoc request
                     :url))))

(defn request-compose!
  [a]
  (let [{:keys [compose-request compose-ready]} (a)]
    (when compose-ready
      (a (fn [w]
           (assoc w
                  :compose-ready
                  false)))
      (let [promise (fetch compose-request)]
        (-> promise
            (.then (fn [response]
                     (.json response)))
            (.then (fn [json]
                     (a (fn [w]
                          (assoc w
                                 :compose-result
                                 (js->clj json
                                          :keywordize-keys true))))))
            (.catch (fn [error]
                      (a (fn [w]
                           (assoc w
                                  :compose-error
                                  error
                                  :compose-error-date (js/Date.)))))))
        (a (fn [w]
             (assoc w
                    :request-compose-promise
                    promise)))))))

(defn add-compose-ready
  [w]
  (if (and (:segments-done w)
           (not (:compose-request w)))
    (assoc w
           :compose-ready true)
    w))

(defn add-compose-request
  [{:keys [compose-url] :as w}]
  (if (:compose-ready w)
    (assoc w
           :compose-request
           {:method :post
            :url (or compose-url
                     "/segment-upload/compose")
            :headers {"Content-Type" "application/json"}
            :body (js/JSON.stringify
                    (clj->js
                      {:uuids (map
                                :segment-uuid
                                (:segments w))}))})
    w
    ))

(defn add-composed-url
  [w]
  (if-let [url (get-in w
                       [:compose-result
                        :url])]
    (if-not (:compose-done w)
      (assoc w
             :compose-done (js/Date.)
             :composed-url url)
      w)
    w))

(defn trigger-retry
  [{:keys [compose-error compose-error-date retry-delay]
    :or {retry-delay 2000}
    :as w}]
  (if compose-error
    (if (<= retry-delay
            (- (.getTime (js/Date.))
               (.getTime compose-error-date)))
      (-> w
          (assoc :compose-ready true)
          (dissoc :compose-error
                  :compose-error-date))
      w)
    w))

(defn compose-drive!
  [a]
  ((:drive! (a)) a)
  (a (fn [w]
       (-> w
           (add-compose-ready)
           (add-compose-request)
           (add-composed-url)
           (trigger-retry)
           )))
  (request-compose! a))

(defn create
  [w]
  (assoc (upload-segments/create w)
         :compose-drive!
         compose-drive!))

(comment
  (do
    (def state
      (atom
        (create
          {
           :blob (js/Blob. [(apply str (interpose "\n"
                                                  (range 32)))]
                           #js {:type "text/plain"}
                           )
           :slice-size 8
           :max-parallel-uploads 8
           })))

    @state

    (def a
      (fn
        ([] @state)
        ([swap-fn] (swap! state
                          swap-fn))))

    (a)
    )

  ((:compose-drive! (a)) a)
  )
