(ns oppai.gen.fleet-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.text :as str]
            [oppai.gen.fleet :as fleet]))

(def sample-map
  "Trimmed from a real `GET /infer/model-map` response (2026-07-31). Kept
  verbatim in shape — including the `family-guess` drift on the LTX nodes,
  because that drift is exactly what the UI has to surface."
  {:ts 1783589234218
   :text {:model-id "qwen3.6-35b-a3b" :status "serving" :node "head"}
   :media [{:node "naphtali" :engine "comfyui" :queue 0
            :checkpoint "ltxv-2b-0.9.6-distilled-04-25.safetensors"
            :model-id "ltxv-2b-0.9.1" :model-kind "ltx-video" :match "family-guess"}
           {:node "zebulun" :engine "comfyui" :queue 0
            :checkpoint "animagine-xl-4.0.safetensors"
            :model-id "animagine-xl-4.0" :model-kind "image" :match "exact"}
           {:node "zebulun" :engine "comfyui" :queue 2
            :checkpoint "waiIllustriousSDXL_v150.safetensors"
            :model-id "wai-illustrious-sdxl-v150" :model-kind "image" :match "exact"}
           {:node "issachar" :engine "comfyui" :queue 1
            :checkpoint "ltxv-2b-0.9.6-distilled-04-25.safetensors"
            :model-id "ltxv-2b-0.9.1" :model-kind "ltx-video" :match "family-guess"}
           {:node "asher" :engine "comfyui" :queue 0
            :checkpoint "svd_xt.safetensors"
            :model-id "svd-xt" :model-kind "video" :match "exact"}]})

(deftest image-models-come-from-the-live-scan
  (let [parsed (fleet/parse-model-map sample-map)
        ids (mapv :model-id (:image parsed))]
    (is (= ["animagine-xl-4.0" "wai-illustrious-sdxl-v150"] ids))
    ;; No hardcoding: everything the picker shows was derived from the scan.
    (is (= "animagine-xl-4.0" (fleet/default-image-model parsed)))))

(deftest nodes-are-folded-per-model-not-per-machine
  (testing "the same checkpoint on two minis is one choice, with queues summed"
    (let [parsed (fleet/parse-model-map sample-map)
          ltx (first (filter #(= "ltxv-2b-0.9.1" (:model-id %)) (:video parsed)))]
      (is (= ["issachar" "naphtali"] (:nodes ltx)))
      (is (= 1 (:queue ltx)))
      ;; upstream could not confirm the exact id — the UI must be able to say so
      (is (false? (:exact? ltx))))))

(deftest fallback-is-marked-as-a-fallback
  (testing "an unreachable map still gives a usable picker, honestly labelled"
    (let [parsed (fleet/parse-model-map {})
          models (fleet/image-models parsed)]
      (is (seq models))
      (is (every? :fallback? models)))))

(deftest video-models-are-not-taken-from-the-mac-mini-scan
  ;; Douga runs on the generation job API, which has its own allowlist. Reading
  ;; the video list off the Mac mini map would offer models that API rejects.
  (is (= ["ltx-2.3" "wan2.2-ti2v-5b" "wan-dancer-14b"]
         (mapv :model-id fleet/video-models)))
  (is (= "ltx-2.3" (fleet/default-video-model))))

(deftest image-request-sends-the-bare-model-id
  (let [body (fleet/image-request {:prompt "  a garden  " :model "animagine-xl-4.0"
                                   :size "1024x1024"})]
    (is (= "a garden" (:prompt body)))
    ;; The gateway appends `.safetensors` itself; sending it surfaced as a
    ;; misleading "node became unreachable mid-render".
    (is (= "animagine-xl-4.0" (:model body)))
    (is (= 1 (:n body)))
    (is (nil? (:negative body)) "empty optionals are omitted, not sent blank")))

(deftest video-request-shapes-to-the-upstream-constraints
  (let [body (fleet/video-request {:prompt "a wave" :size "768x448" :frames 49})]
    (is (= "video" (:type body)))
    (is (= "ltx-2.3" (:model body)))
    (is (= {:width 768 :height 448 :frames 49} (:params body)))
    (is (= "a wave" (get-in body [:input :prompt]))))
  (testing "an unparseable size falls back to the upstream default, not to nil"
    (is (= 768 (get-in (fleet/video-request {:prompt "x" :size "huge"}) [:params :width])))))

(deftest job-status-drops-every-upstream-url
  ;; Both URLs the API returns point at murakumo.cloud, which 401s for these
  ;; tokens (different worker, different secret) — measured on a real finished
  ;; job. Keeping either would invite a caller to follow it.
  (let [job (fleet/job-status {:jobId "abc" :status "running" :progress 10
                               :statusUrl "https://murakumo.cloud/api/v1/generation/jobs/abc"
                               :outputKind "mp4" :artifacts []})]
    (is (= "abc" (:job/id job)))
    (is (= :running (:job/status job)))
    (is (not (contains? job :job/status-url)))
    (is (nil? (:job/artifact-url job)) "no artifact yet — no URL to offer")
    (is (false? (fleet/terminal? job))))
  (is (true? (fleet/terminal? (fleet/job-status {:jobId "x" :status "done"}))))
  (is (true? (fleet/terminal? (fleet/job-status {:jobId "x" :status "failed"})))))

(deftest a-finished-artifact-is-served-same-origin
  ;; The URL in the envelope is unusable from a browser twice over: wrong host
  ;; (401) and, on the right host, it needs the Bearer token the browser must
  ;; not hold. Rendering it would give the user a <video> that silently fails.
  (let [job (fleet/job-status
             {:jobId "c303f2f8" :status "done" :progress 100 :outputKind "mp4"
              :artifacts [{:kind "mp4" :bytes 285262
                           :url "https://murakumo.cloud/api/v1/generation/jobs/c303f2f8/artifact"}]})]
    (is (= "/api/generation/jobs/c303f2f8/artifact" (:job/artifact-url job)))
    (is (not (str/includes? (:job/artifact-url job) "murakumo.cloud")))))

(deftest image-b64-reads-the-gateway-shape
  (is (= "AAA" (fleet/image-b64 {:data [{:b64_json "AAA"}]})))
  (is (nil? (fleet/image-b64 {:data []}))))
