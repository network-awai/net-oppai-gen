(ns oppai.gen.route-test
  (:require [clojure.test :refer [deftest is]]
            [oppai.gen.route :as route]))

(deftest every-view-has-an-address-and-round-trips
  (doseq [{:keys [id fragment]} route/views]
    (is (= id (route/fragment->view (str "#" fragment))))
    (is (= id (route/fragment->view fragment)) "with or without the hash")
    (is (= (str "#" fragment) (route/view->fragment id))))
  (is (= (count route/views) (count (set (map :fragment route/views))))))

(deftest unknown-addresses-land-on-the-default
  (is (= route/default-view (route/fragment->view "")))
  (is (= route/default-view (route/fragment->view nil)))
  (is (= route/default-view (route/fragment->view "#nope")))
  (is (= (route/view->fragment route/default-view) (route/view->fragment :nope)))
  (is (route/view? :models))
  (is (not (route/view? :nope))))
