(ns simplemono.segment-upload.util)

(defn random-uuid
  []
  (letfn [(hex [] (.toString (rand-int 16) 16))]
    (let [rhex (.toString (bit-or 0x8 (bit-and 0x3 (rand-int 16))) 16)]
      (str (hex) (hex) (hex) (hex)
           (hex) (hex) (hex) (hex) "-"
           (hex) (hex) (hex) (hex) "-"
           "4" (hex) (hex) (hex) "-"
           rhex (hex) (hex) (hex) "-"
           (hex) (hex) (hex) (hex)
           (hex) (hex) (hex) (hex)
           (hex) (hex) (hex) (hex)))))

(defn a-in
  [a path]
  (fn
    ([]
     (get-in (a)
             path))
    ([swap-fn]
     (a (fn [w*]
          (update-in w*
                     path
                     swap-fn))))))

(defn atom->a
  [state]
  (fn
    ([] @state)
    ([swap-fn] (swap! state
                      swap-fn))))
