(ns oppai.gen.chat
  "The chat wire, as pure functions: request body, SSE frame parsing, and the
  reasoning-trace strip. Portable so both runtimes and the tests agree on one
  implementation."
  (:require [clojure.string :as str]
            [oppai.gen.fleet :as fleet]))

(def max-output-tokens
  "The public murakumo chat route caps unauthenticated requests at 2048 and
  silently clamps above it. Asking for more than we can get would make the
  ceiling look like ours."
  2048)

(defn request-body
  [{:keys [messages max-tokens temperature stream?]}]
  {:model fleet/chat-model
   :messages (vec messages)
   :stream (boolean stream?)
   :max_tokens (min (or max-tokens 1024) max-output-tokens)
   :temperature (let [t temperature] (if (number? t) (max 0 (min 2 t)) 0.7))
   ;; Load-bearing. The current fleet main is a hybrid-thinking model: without
   ;; this it spends the whole token budget on `reasoning_content` and returns
   ;; an empty `content` with finish_reason "length" — i.e. the user sees the
   ;; assistant say nothing at all.
   :chat_template_kwargs {:enable_thinking false}})

(defn strip-think
  "Remove `<think>…</think>` spans. Belt and braces next to
  `enable_thinking false`: the flag is honoured by the template, but a model
  can still emit the tags in-band, and leaking a reasoning trace into the
  visible answer is both confusing and a privacy smell.

  A negated-pair character class rather than the `(?s)` inline flag: `(?s)` is a Java construct
  that JavaScript does not implement, so on the browser runtime the dot
  stopped at the first newline and a multi-line trace leaked through with its
  closing tag. The JVM-only test suite could not see that; the portable nbb
  suite does (2026-09-06)."
  [text]
  (-> (str text)
      (str/replace #"<think>[\s\S]*?</think>" "")
      (str/replace #"<think>[\s\S]*" "")
      (str/replace #"^\s+" "")))

(defn sse-payload
  "One SSE line -> its `data:` payload string, `::done`, or nil.

  Deliberately does no JSON parsing: keeping the framing pure means the JVM
  tests exercise the same code the browser runs, instead of a `:clj` branch
  that exists only to be skipped."
  [line]
  (let [line (str/trim (str line))]
    (when (and (seq line) (str/starts-with? line "data:"))
      (let [payload (str/trim (subs line 5))]
        (if (= "[DONE]" payload) ::done payload)))))

(def done ::done)

(defn delta-from-chunk
  "An already-parsed OpenAI stream chunk -> its content delta, or nil.

  Both `choices[0].delta.content` and the non-streaming
  `choices[0].message.content` are read: the fleet has answered in both shapes
  depending on which head served the request, and treating one of them as 'no
  content' loses the whole reply."
  [chunk]
  (let [c (get-in chunk [:choices 0])
        delta (or (get-in c [:delta :content])
                  (get-in c [:message :content]))]
    (when (and (string? delta) (seq delta)) delta)))

(defn split-frames
  "Split a decoded SSE chunk buffer into [complete-lines remainder].

  A network chunk can end mid-line; handing a half-line to the JSON parser is
  how a stream develops a mysterious dropped token."
  [buffer]
  (let [parts (str/split (str buffer) #"\n" -1)]
    [(vec (butlast parts)) (last parts)]))
