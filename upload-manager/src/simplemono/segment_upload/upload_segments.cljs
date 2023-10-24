(ns simplemono.segment-upload.upload-segments
  (:require [simplemono.segment-upload.upload-segment :as upload-segment]
            [simplemono.segment-upload.util :as util]
            ))

(defn get-slice-ranges
  [slice-size blob-size]
  (let [slice-count (js/Math.ceil (/ blob-size
                                     slice-size))]
    (concat
      (map
        (fn [slice-n]
          (let [start (* slice-size
                         slice-n)
                end (+ start
                       slice-size)]
            [start end]))
        (range (dec slice-count)))
      [[(* (dec slice-count)
           slice-size)
        blob-size]])))

(defn add-segments
  [{:keys [slice-size blob segment-upload-url segments] :as w}]
  (if-not segments
    (assoc w
           :segments
           (vec
             (map-indexed
               (fn [index slice-range]
                 (let [[start end] slice-range
                       segment-size (- end start)]
                   {:index index
                    :segment-uuid (util/random-uuid)
                    :progress 0
                    :size segment-size
                    :slice-range slice-range
                    :segment-upload-url segment-upload-url}))
               (get-slice-ranges slice-size
                                 (.-size blob))))
           :blob-size (.-size blob))
    w))

(defn uploaded-bytes-count
  [{:keys [segments]}]
  (apply +
         (map
           (fn [segment]
             (* (:progress segment)
                (:size segment)))
           segments)))

(defn get-progress
  [{:keys [blob-size] :as w}]
  (/ (uploaded-bytes-count w)
     blob-size))

(defn get-pending-uploads
  [w]
  (filter
    (fn [segment]
      (or (not (:started segment))
          (:error segment)))
    (:segments w)))

(defn get-active-uploads
  [w]
  (filter
    (fn [segment]
      (and (:started segment)
           (not (:error segment))
           (not (:done segment))))
    (:segments w)))

(defn slice-segment
  [w index]
  (update-in
    w
    [:segments
     index]
    (fn [{:keys [slice-range segment-blob] :as segment}]
      (if segment-blob
        segment
        (let [[start end] slice-range]
          (assoc segment
                 :segment-blob
                 (.slice (:blob w)
                         start
                         end)))))))

(defn start-segment-upload!
  [a index]
  (a (fn [w]
       (slice-segment w
                      index)))
  (let [a* (util/a-in a
                      [:segments
                       index])
        upload-segment! (:upload-segment! (a))]
    (upload-segment! a*)))

(defn update-progress
  [w]
  (update
    w
    :progress
    (fn [progress]
      ;; We noticed
      ;; that [ProgressEvent](https://developer.mozilla.org/en-US/docs/Web/API/XMLHttpRequestUpload/progress_event)
      ;; sometimes jumps back from 100% upload progress to something like 35%.
      ;; We did not find out the reason to avoid that the progress glitches back
      ;; to a way lower precentage `max` is being used here:
      (max (or progress
               0)
           (get-progress w)))))

(defn cleanup-slices
  "Free up memory of blob slices, where the segment already has been uploaded
   successfully."
  [w]
  (update w
          :segments
          (fn [segments]
            (into []
                  (map
                    (fn [segment]
                      (if (:done segment)
                        (dissoc segment
                                :segment-blob)
                        segment))
                    segments)))))

(defn all-done?
  [w]
  (every? :done
          (:segments w)))

(defn done-status
  [w]
  (if (all-done? w)
    (-> w
        (assoc :segments-done true)
        (dissoc :abort))
    w))

(defn retry?
  "Returns true, when the 2-4 seconds has passed, since the error happened."
  [segment]
  (boolean
    (and (:error segment)
         (< (+ (.getTime (:error-date segment))
               2000
               (rand-int 2000))
            (.getTime (js/Date.))))))

(defn start-uploads!
  [a]
  (let [w (a)
        active (get-active-uploads w)
        pending (get-pending-uploads w)]
    (when (< (count active)
             (:max-parallel-uploads w))
      (doseq [segment (take (:max-parallel-uploads w)
                            pending)]
        (when (or (not (:started segment))
                  ;; Slows down the number of retry attempts per `drive!` call:
                  (and (:error segment)
                       (retry? segment)))
          (start-segment-upload! a
                                 (:index segment)))))))

(defn abort
  [a]
  (a (fn [w]
       (doseq [segment (:segments w)]
         (when-let [abort* (:abort segment)]
           (try
             (abort*)
             (catch :default e
               (js/console.error e
                                 "failed to abort segment upload")))))
       (-> w
           (assoc :aborted (js/Date.))
           (dissoc :abort)))))

(defn add-abort
  [w a]
  (if-not (:abort w)
    (assoc w
           :abort
           (partial abort
                    a))
    w))

(defn drive!
  [a]
  (when-not (:aborted (a))
    (a (fn [w]
         (-> w
             (add-segments)
             (add-abort a)
             (update-progress)
             (cleanup-slices)
             (done-status)))))
  (let [w (a)]
    (when (and (not (:aborted w))
               (not (:segments-done w)))
      (start-uploads! a))))

(def defaults
  {:segment-upload-url "/segment-upload/upload/"
   :slice-size (* 1024 1024)
   :max-parallel-uploads 2
   :upload-segment! upload-segment/upload-segment!
   :drive! drive!})

(defn create
  [w]
  (merge defaults
         w))

(comment
  (get-slice-ranges 8
                    32)

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
           :max-parallel-uploads 2
           })))

    @state

    (swap! state
           add-segments)

    (get-progress @state)

    (def a
      (fn
        ([] @state)
        ([swap-fn] (swap! state
                          swap-fn))))

    (a)
    )

  (do
    ((:drive! (a)) a)

    [(:progress (a))
     (count (get-active-uploads (a)))]
    )


  )
