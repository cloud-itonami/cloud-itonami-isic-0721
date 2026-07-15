(ns uraniumops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean extraction-record
  logging request through intake -> advise -> govern -> decide ->
  approval -> commit at phase 1 (assisted-extraction, always
  approval), then re-runs the same op at phase 3 (supervised-auto,
  clean + high confidence -> auto-commit), then a mining-operation-
  scheduling request (also auto-commit clean at phase 3), then a
  shipment-coordination request (ALWAYS escalates, even at phase 3 --
  IAEA-safeguarded material), then a radiological-concern flag (ALSO
  ALWAYS escalates, at any phase -- approve, then commit), then
  HARD-hold scenarios: an unregistered site, a site registered but
  whose radiological/mining permit is not yet verified, a proposal
  whose own `:effect` is not `:propose`, and a proposal that has
  drifted into the permanently-excluded mining-equipment-control/
  radiation-safety-authority scope."
  (:require [langgraph.graph :as g]
            [uraniumops.advisor :as advisor]
            [uraniumops.store :as store]
            [uraniumops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "radiation-safety-officer-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        operator-phase-1 {:actor-id "op-1" :actor-role :shift-supervisor :phase 1}
        operator-phase-3 {:actor-id "op-1" :actor-role :shift-supervisor :phase 3}
        actor (op/build db)]

    (println "== log-extraction-record uth-site-1 (phase 1, escalates -- human approves) ==")
    (println (exec-op actor "t1" {:op :log-extraction-record :site-id "uth-site-1"
                                  :patch {:tonnage 850 :grade-pct 0.31 :shift "day"}} operator-phase-1))
    (println (approve! actor "t1"))

    (println "== log-extraction-record uth-site-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-extraction-record :site-id "uth-site-1"
                                  :patch {:tonnage 910 :grade-pct 0.29 :shift "night"}} operator-phase-3))

    (println "== schedule-mining-operation uth-site-2 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-mining-operation :site-id "uth-site-2"
                                  :patch {:crew "wellfield-crew-3" :window "2026-07-20"}} operator-phase-3))

    (println "== coordinate-shipment uth-site-1 (ALWAYS escalates, even at phase 3 -- IAEA-safeguarded material) ==")
    (let [r (exec-op actor "t4" {:op :coordinate-shipment :site-id "uth-site-1"
                                 :patch {:carrier "secure-rail-co-1" :tonnage 850}} operator-phase-3)]
      (println r)
      (println "-- human radiation-safety officer reviews & approves --")
      (println (approve! actor "t4")))

    (println "== flag-radiological-concern uth-site-2 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :flag-radiological-concern :site-id "uth-site-2"
                                 :patch {:concern "elevated gamma reading near ion-exchange skid" :confidence 0.95}} operator-phase-3)]
      (println r)
      (println "-- human radiation-safety officer reviews & approves --")
      (println (approve! actor "t5")))

    (println "== log-extraction-record uth-site-9 (unregistered site -> HARD hold) ==")
    (println (exec-op actor "t6" {:op :log-extraction-record :site-id "uth-site-9"
                                  :patch {:tonnage 100}} operator-phase-3))

    (println "== log-extraction-record uth-site-3 (registered but permit unverified -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-extraction-record :site-id "uth-site-3"
                                  :patch {:tonnage 100}} operator-phase-3))

    (println "== coordinate-shipment uth-site-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer db req) :effect :commit)))})]
      (println (exec-op actor-direct "t8" {:op :coordinate-shipment :site-id "uth-site-1"
                                           :patch {:carrier "secure-rail-co-1"}} operator-phase-3)))

    (println "== schedule-mining-operation uth-site-1, advisor drifts into wellfield-pump/drill-and-blast scope -> HARD hold, permanent ==")
    (println (exec-op actor "t9" {:op :schedule-mining-operation :site-id "uth-site-1"
                                  :out-of-scope? true
                                  :patch {}} operator-phase-3))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))
