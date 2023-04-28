(ns simplemono.segment-upload.upload-manager
  (:require [simplemono.segment-upload.compose-segments :as compose-segments]
            [simplemono.segment-upload.util :as util]
            ))

(defn ensure-id
  [upload]
  (if (:id upload)
    upload
    (assoc upload
           :id
           (js/crypto.randomUUID))))

(defn add-upload
  [w upload]
  (let [upload* (-> upload
                    ensure-id
                    (assoc :added (js/Date.)))]
    (assoc-in w
              [:uploads
               (:id upload*)]
              (compose-segments/create upload*))))

(defn active-uploads
  [w]
  (filter
    (fn [[_id upload]]
      (and (not (:compose-done upload))
           (:started upload)))
    (:uploads w)))

(defn next-upload
  [{:keys [max-parallel-uploads uploads] :as w}]
  (if (< (count (active-uploads w))
         max-parallel-uploads)
    (if-let [next (first
                    (remove
                      :started
                      (sort-by :added
                               (vals uploads))))]
      (assoc-in w
                [:uploads
                 (:id next)
                 :started]
                (js/Date.))
      w)
    w))

(defn callback!
  [w]
  (update
    w
    :uploads
    (fn [uploads]
      (into {}
            (map
              (fn [[id upload]]
                [id
                 (let [{:keys [compose-done
                               callback
                               callback-invoked]} upload]
                   (if (and compose-done
                            callback
                            (not callback-invoked))
                     (let [upload* (assoc upload
                                          :callback-invoked
                                          true)]
                       (try
                         (callback {:url (:composed-url upload)})
                         upload*
                         (catch :default e
                           (js/console.error
                             e
                             (str "error during callback of upload: "
                                  (:id upload)))
                           upload*)))
                     upload))]))
            uploads))))

(defn cleanup
  [{:keys [cleanup-timeout]
    :or {cleanup-timeout 5000} :as w}]
  (update w
          :uploads
          (fn [uploads]
            (into {}
                  (filter
                    (fn [[_id upload]]
                      (if-let [done (or (:compose-done upload)
                                        (:aborted upload))]
                        (let [now (js/Date.)
                              timeout-at (+ (.getTime done)
                                            cleanup-timeout)]
                          (<= (.getTime now)
                              timeout-at))
                        true)))
                  uploads))))

(defn drive!
  [a]
  (a (fn [w]
       (-> w
           (next-upload)
           (callback!)
           (cleanup))))
  (doseq [[_id upload] (:uploads (a))]
    ((:compose-drive! upload)
     (util/a-in a
                [:uploads
                 (:id upload)]))))

(defn create
  [w]
  (merge {:max-parallel-uploads 2
          :drive! drive!}
         w))

(comment
  (do
    (def state
      (atom
        {:max-parallel-uploads 2}))

    (def a
      (fn
        ([] @state)
        ([swap-fn] (swap! state
                          swap-fn))))

    (a (fn [w]
         (add-upload w
                     {
                      :id "1"
                      :blob (js/Blob. [(apply str (interpose "\n"
                                                             (range 32)))]
                                      #js {:type "text/plain"}
                                      )
                      :callback (fn [{:keys [url]}]
                                  (js/console.log "upload done. URL:"
                                                  url))
                      :slice-size 8
                      :max-parallel-uploads 8
                      })))
    )

  (do (drive! a)
      (a))

  (active-uploads (a))

  (js/console.log *e)
  )
