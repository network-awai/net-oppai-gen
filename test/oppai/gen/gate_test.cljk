(ns oppai.gen.gate-test
  (:require [clojure.test :refer [deftest is]]
            [oppai.gen.db :as db]))

(deftest fresh-visitor-is-not-confirmed
  (is (false? (db/age-confirmed? (db/initial-db)))))

(deftest confirm-age-flips-the-gate-and-only-the-gate
  (let [d (db/confirm-age (db/initial-db))]
    (is (true? (db/age-confirmed? d)))
    ;; The gate is an overlay state, not a content state: confirming it must
    ;; not pre-select models or start anything.
    (is (= :idle (get-in d [:image :status])))
    (is (= :idle (get-in d [:douga :status])))))

(deftest age-confirmed?-is-falsey-safe
  ;; A db produced by an older build (or a partial test fixture) with no
  ;; :age-confirmed? key must read as NOT confirmed, never nil-as-true.
  (is (false? (db/age-confirmed? {:tab :chat}))))
