(ns oppai.gen.chat-test
  (:require [clojure.test :refer [deftest is testing]]
            [oppai.gen.chat :as chat]))

(deftest request-body-never-hardcodes-a-model-and-disables-thinking
  (let [b (chat/request-body {:messages [{:role "user" :content "hi"}]})]
    ;; The fleet-main ALIAS, so a model swap upstream needs no deploy here.
    (is (= "murakumo-main" (:model b)))
    ;; Load-bearing: the current main is a hybrid-thinking model that
    ;; otherwise spends the whole budget on reasoning_content and returns an
    ;; empty content with finish_reason "length".
    (is (false? (get-in b [:chat_template_kwargs :enable_thinking])))))

(deftest max-tokens-is-clamped-to-what-the-public-route-will-actually-give
  (is (= 2048 (:max_tokens (chat/request-body {:messages [] :max-tokens 99999}))))
  (is (= 256 (:max_tokens (chat/request-body {:messages [] :max-tokens 256}))))
  (is (= 1024 (:max_tokens (chat/request-body {:messages []})))))

(deftest sse-framing-is-pure
  (is (= "{\"a\":1}" (chat/sse-payload "data: {\"a\":1}")))
  (is (= chat/done (chat/sse-payload "data: [DONE]")))
  (is (nil? (chat/sse-payload "")))
  (is (nil? (chat/sse-payload ": keepalive")))
  (is (nil? (chat/sse-payload "event: ping"))))

(deftest split-frames-holds-back-a-partial-line
  (testing "a chunk ending mid-line must not be handed to the parser"
    (is (= [["a" "b"] "par"] (chat/split-frames "a\nb\npar")))
    (is (= [["a"] ""] (chat/split-frames "a\n")))
    (is (= [[] "nolinebreak"] (chat/split-frames "nolinebreak")))))

(deftest delta-reads-both-streaming-and-non-streaming-shapes
  (is (= "x" (chat/delta-from-chunk {:choices [{:delta {:content "x"}}]})))
  (is (= "y" (chat/delta-from-chunk {:choices [{:message {:content "y"}}]})))
  (is (nil? (chat/delta-from-chunk {:choices [{:delta {}}]})))
  (is (nil? (chat/delta-from-chunk {}))))

(deftest think-spans-never-reach-the-reader
  (is (= "answer" (chat/strip-think "<think>secret plan</think>answer")))
  (is (= "answer" (chat/strip-think "<think>a\nb\nc</think>\nanswer")))
  (testing "an unclosed span (mid-stream) still hides the trace"
    (is (= "" (chat/strip-think "<think>still reasoning")))))
