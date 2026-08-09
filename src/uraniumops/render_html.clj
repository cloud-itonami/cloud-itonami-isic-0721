(ns uraniumops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave2): this repo previously had NO operator-console generator. This
  namespace drives the REAL actor stack (`uraniumops.operation` ->
  `uraniumops.governor` -> `uraniumops.store`) through a scenario
  adapted from this repo's own `uraniumops.sim` demo driver
  (`clojure -M:dev:run`, confirmed BEFORE writing this file to produce a
  sensible ledger against the real seeded site ids `uth-site-1`..
  `uth-site-3` -- ids that DO match `uraniumops.store/demo-data`, so it
  was safe to reuse rather than author from scratch), covering clean
  auto-commits, always-escalate ops that a human then approves, and
  four distinct HARD-hold reasons, rendered deterministically -- no
  invented numbers, no timestamps in the page content, byte-identical
  across reruns against the same seed (verify by diffing two consecutive
  runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [uraniumops.advisor :as advisor]
            [uraniumops.store :as store]
            [uraniumops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator-phase-1
  {:actor-id "op-1" :actor-role :shift-supervisor :phase 1})

(def ^:private operator-phase-3
  {:actor-id "op-1" :actor-role :shift-supervisor :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "radiation-safety-officer-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: uth-site-1 phase-1 extraction-record escalates
  then human-approves; same site phase-3 clean extraction-record
  auto-commits; uth-site-2 phase-3 mining-operation schedule auto-commits;
  uth-site-1 coordinate-shipment ALWAYS escalates (IAEA-safeguarded) then
  approved; uth-site-2 flag-radiological-concern ALWAYS escalates then
  approved; uth-site-9 HARD-holds as unregistered; uth-site-3 HARD-holds
  as registered-but-permit-unverified; advisor-injected `:effect :commit`
  HARD-holds on effect-not-propose; out-of-scope mining-equipment
  proposal HARD-holds permanently on scope-excluded. Every HARD hold
  never reaches a human. Returns the resulting store -- every field
  read by `render` below is real governor/store output, not a hand-typed
  copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "t1" {:op :log-extraction-record :site-id "uth-site-1"
                       :patch {:tonnage 850 :grade-pct 0.31 :shift "day"}}
           operator-phase-1)
    (approve! actor "t1")

    (exec! actor "t2" {:op :log-extraction-record :site-id "uth-site-1"
                       :patch {:tonnage 910 :grade-pct 0.29 :shift "night"}}
           operator-phase-3)

    (exec! actor "t3" {:op :schedule-mining-operation :site-id "uth-site-2"
                       :patch {:crew "wellfield-crew-3" :window "2026-07-20"}}
           operator-phase-3)

    (exec! actor "t4" {:op :coordinate-shipment :site-id "uth-site-1"
                       :patch {:carrier "secure-rail-co-1" :tonnage 850}}
           operator-phase-3)
    (approve! actor "t4")

    (exec! actor "t5" {:op :flag-radiological-concern :site-id "uth-site-2"
                       :patch {:concern "elevated gamma reading near ion-exchange skid"
                               :confidence 0.95}}
           operator-phase-3)
    (approve! actor "t5")

    (exec! actor "t6" {:op :log-extraction-record :site-id "uth-site-9"
                       :patch {:tonnage 100}}
           operator-phase-3)

    (exec! actor "t7" {:op :log-extraction-record :site-id "uth-site-3"
                       :patch {:tonnage 100}}
           operator-phase-3)

    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer db req) :effect :commit)))})]
      (exec! actor-direct "t8" {:op :coordinate-shipment :site-id "uth-site-1"
                                :patch {:carrier "secure-rail-co-1"}}
             operator-phase-3))

    (exec! actor "t9" {:op :schedule-mining-operation :site-id "uth-site-1"
                       :out-of-scope? true
                       :patch {}}
           operator-phase-3)
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- hold-rule [f]
  (or (some-> f :basis first)
      (some-> f :violations first :rule)))

(defn- last-fact-for [ledger site-id]
  (last (filter #(= (:site-id %) site-id) ledger)))

(defn- status-cell [ledger site-id]
  (let [f (last-fact-for ledger site-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (hold-rule f)]
        (str "<span class=\"critical\">HARD hold &middot; "
             (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- permit-cell [{:keys [registered? permit-verified?]}]
  (cond
    (and registered? permit-verified?)
    "<span class=\"ok\">registered &amp; permit verified</span>"
    registered?
    "<span class=\"warn\">registered, permit unverified</span>"
    :else
    "<span class=\"critical\">unregistered</span>"))

(defn- site-row [ledger {:keys [site-id] :as s}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc site-id)
          (esc (:name s))
          (esc (name (or (:ore s) :n-a)))
          (esc (name (or (:method s) :n-a)))
          (permit-cell s)
          (status-cell ledger site-id)))

(defn- ledger-row [{:keys [t op site-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t))
          (esc (name (or op :n-a)))
          (esc site-id)
          (esc (or (some->> basis (map name) (str/join ", "))
                   (some-> disposition name)
                   ""))))

(defn- coordination-row [{:keys [op site-id value]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name (or op :n-a)))
          (esc site-id)
          (esc (pr-str (or value {})))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README Operations / `uraniumops.governor`/`uraniumops.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-extraction-record</code></td><td><span class=\"ok\">phase-1 always approval; phase-3 auto-commit when clean &amp; high confidence</span></td></tr>"
   "        <tr><td><code>:schedule-mining-operation</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &amp; high confidence</span></td></tr>"
   "        <tr><td><code>:flag-radiological-concern</code></td><td><span class=\"warn\">ALWAYS human approval · never auto at any phase · surfaces concern only, never radiation-safety authority decisions</span></td></tr>"
   "        <tr><td><code>:coordinate-shipment</code></td><td><span class=\"warn\">ALWAYS human approval (IAEA-safeguarded nuclear material) · never auto at any phase</span></td></tr>"
   "        <tr><td>mine-site unregistered / permit unverified</td><td><span class=\"critical\">HARD hold · never reaches a human</span></td></tr>"
   "        <tr><td>effect not <code>:propose</code> / scope-excluded equipment or radiation-safety-authority</td><td><span class=\"critical\">HARD hold permanent · never overridable</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        sites (store/all-sites db)
        site-rows (str/join "\n" (map (partial site-row ledger) sites))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        coord-rows (str/join "\n" (map coordination-row (store/coordination-log db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-0721 &middot; uranium-thorium-ore mining ops</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Uranium and thorium ore mining operations coordination (ISIC 0721) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · radiological-concern / IAEA shipment always human-approved · HARD holds permanent</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Uranium / thorium mine sites</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>uraniumops.store</code> via <code>uraniumops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated from the real actor stack. Covers both ISIC-0721 extraction methods (conventional + in-situ recovery) and both ores (uranium + thorium).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Site</th><th>Name</th><th>Ore</th><th>Method</th><th>Registration / permit</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     site-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (UraniumThoriumMiningGovernor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden: mine-site/permit unverified (registration + radiological/mining permit), effect not <code>:propose</code>, and permanently out-of-scope mining-equipment-control / radiation-safety(-certification-authority) territory. An unregistered or permit-unverified site never reaches a human. <code>:flag-radiological-concern</code> and <code>:coordinate-shipment</code> always escalate when otherwise clean (IAEA safeguards for the latter).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op / condition</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Committed coordination log (this run)</h2>\n"
     "    <p class=\"muted\">Records written only after governor-clean commit (auto or human-approved).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Site</th><th>Value</th></tr></thead>\n"
     "      <tbody>\n"
     coord-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Site</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)
        parent (.getParentFile (java.io.File. out))]
    (when parent (.mkdirs parent))
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "coordination commits )")))
