(ns simplemono.segment-upload.upload-manager-ui)

(defn abort-button
  [{:keys [upload]}]
  [:button
   {:type "button"
    :class
    (cond-> ["rounded-full" "bg-red-300" "p-1" "text-white" "shadow-sm" "hover:bg-red-600" "focus-visible:outline" "focus-visible:outline-2" "focus-visible:outline-offset-2" "focus-visible:outline-red-600"]
      (not (:abort upload))
      (conj "invisible"))
    :onClick (fn [e]
               (when (js/confirm "Do you want to abort this upload?")
                 ((:abort upload) e)))
    }
   [:svg
    {:xmlns "http://www.w3.org/2000/svg",
     :fill "none",
     :viewBox "0 0 24 24",
     :stroke-width "1.5",
     :stroke "currentColor",
     :class "w-6 h-6"}
    [:path
     {:stroke-linecap "round",
      :stroke-linejoin "round",
      :d "M6 18L18 6M6 6l12 12"}]]])

(defn progress-bar
  [{:keys [upload]}]
  [:div
   {:class "w-full bg-gray-200 rounded-full h-2.5 dark:bg-gray-700"}
   [:div {:class "bg-blue-600 h-2.5 rounded-full"
          :style {:width (str (* (:progress upload
                                            0)
                                 100)
                              "%")}}]])

(defn upload-label
  [{:keys [upload]}]
  [:div
   [:div
    {:class "flex justify-between mb-1"}
    [:span
     {:class "text-base font-medium text-blue-700 dark:text-white"}
     (or (:name upload)
         (:id upload))]
    [:span
     {:class "text-sm font-medium text-blue-700 dark:text-white"}
     (str (long (* (:progress upload
                              0)
                   100))
          "%")]]])

(defn upload-item
  [params]
  [:div.flex.justify-between.p-2
   {:class [(when (:aborted (:upload params))
              "opacity-25")]}
   [:div.w-full.pt-3.pb-3
    [upload-label params]
    [:div.flex
     [progress-bar params]]]
   [:div.pt-3.pb-3.pl-3
    [abort-button params]]])

(defn upload-manager [params]
  [:div
   (map
     (fn [upload]
       ^{:key (:id upload)}
       [upload-item (assoc params
                           :upload
                           upload)])
     (remove
       ;; option to hide uploads:
       :hidden
       (sort-by :added
                (vals (:uploads params)))))])
