(ns simplemono.segment-upload.upload-manager-ui-devcard
  (:require [devcards.core :as dc :refer [defcard]]
            [simplemono.segment-upload.upload-manager-ui :as upload-manager-ui]
            [simplemono.segment-upload.upload-manager :as upload-manager]
            [simplemono.segment-upload.util :as util]
            [reagent.core :as reagent]
            ))

(def example-w
  {:max-parallel-uploads 2,
   :upload-queue [{:id "1"
                   :segment-upload-url "/segment-upload/upload/"
                   }
                  {:id "2"
                   :segment-upload-url "/segment-upload/upload/"
                   }]
   :uploads
   {"0"
    {:started #inst "2023-04-25T14:52:34.174-00:00",
     :segment-upload-url "/segment-upload/upload/",
     :abort (fn []
              (js/alert "upload aborted"))
     :segments
     [{:started true,
       :done true,
       :index 0,
       :segment-upload-url "/segment-upload/upload/",
       :segment-uuid "e19a6f0e-130b-49e0-80b9-18e879285e57",
       :progress 1,
       :slice-range [0 8]}
      {:started true,
       :done true,
       :index 1,
       :segment-upload-url "/segment-upload/upload/",
       :segment-uuid "36e22e5c-5b11-474c-bdc8-1f9aeb471d1c",
       :progress 1,
       :slice-range [8 16]}]
     :slice-size 8,
     :id "0",
     :progress 0.7272727272727273}},
   :now #inst "2023-04-25T14:52:35.361-00:00"})

(defcard layout
  (dc/reagent
    [:div.bg-gray-100
     [upload-manager-ui/upload-manager
      example-w]]))

(defcard demo
  (dc/reagent
    (fn [data-atom]
      (let [a (util/atom->a data-atom)]
        [:div.bg-gray-100
         [upload-manager-ui/upload-manager
          @data-atom]
         [:button
          {:onClick (fn [_e]
                      ((:drive! @data-atom) a))
           :class
           "rounded bg-indigo-600 px-2 py-1 text-xs font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
           }
          "drive!"]
         [:button.ml-2
          {:onClick (fn [_e]
                      (js/console.log "enqueue")
                      (a (fn [w]
                           (upload-manager/add-upload
                             w
                             {
                              :id "1"
                              :blob (js/Blob. [(apply str (interpose "\n"
                                                                     (range 32)))]
                                              #js {:type "text/plain"}
                                              )
                              :callback (fn [{:keys [url]}]
                                          (js/alert (str "upload done. URL: "
                                                         url)))
                              :slice-size 8
                              :max-parallel-uploads 8
                              }))))
           :class
           "rounded bg-indigo-600 px-2 py-1 text-xs font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"}
          "enqueue"]])))
  (reagent/atom (upload-manager/create {}))
  {:inspect-data true})

(defcard demo-with-input-field
  (dc/reagent
    (fn [data-atom]
      (let [a (util/atom->a data-atom)]
        [:div.bg-gray-100
         [upload-manager-ui/upload-manager
          @data-atom]
         [:input
          {:type "file"
           :on-change (fn [e]
                        (a (fn [w]
                             (let [file (first e.target.files)]
                               (upload-manager/add-upload
                                 w
                                 {:name (.-name file)
                                  :blob file
                                  :callback (fn [{:keys [url]}]
                                              (js/alert (str "upload done. URL: "
                                                             url)))
                                  :max-parallel-uploads 8
                                  })))))}]
         [:button
          {:onClick (fn [_e]
                      ((:drive! @data-atom) a))
           :class
           "rounded bg-indigo-600 px-2 py-1 text-xs font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
           }
          "drive!"]
         [:button.ml-2
          {:onClick (fn [_e]
                      ;; The way to drive the complete upload-process:
                      (js/setInterval (fn []
                                        ((:drive! @data-atom) a))
                                      200)
                      )
           :class
           "rounded bg-indigo-600 px-2 py-1 text-xs font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"}
          "auto-drive!"]])))
  (reagent/atom (upload-manager/create {}))
  ;; {:inspect-data true}
  )
