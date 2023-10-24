(ns simplemono.segment-upload.util)

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
