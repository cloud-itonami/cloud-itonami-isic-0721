(ns uraniumops.phase
  "Phase 0->3 staged rollout for the ISIC-0721 uranium/thorium-ore
  mining operations-coordination actor.

    Phase 0  read-only              -- no writes, still governor-gated.
    Phase 1  assisted-extraction    -- extraction-record logging
                                        allowed, every write needs
                                        human approval.
    Phase 2  assisted-coordination  -- adds mining/haulage-operation
                                        scheduling and shipment
                                        coordination writes, still
                                        approval.
    Phase 3  supervised auto        -- governor-clean, high-confidence
                                        `:log-extraction-record`/
                                        `:schedule-mining-operation`
                                        may auto-commit.
                                        `:flag-radiological-concern`/
                                        `:coordinate-shipment` NEVER
                                        auto-commit, at any phase.

  `:flag-radiological-concern` and `:coordinate-shipment` are
  deliberately ABSENT from every phase's `:auto` set, including phase
  3 -- a permanent structural fact, not a rollout milestone still to
  come. Surfacing a radiological concern, and coordinating the
  shipment of IAEA-safeguarded nuclear material, both always need a
  human to actually look at it. `uraniumops.governor`'s own
  `always-escalate-ops` enforces the same invariant independently --
  two layers, not one, agree on this."
  (:require [uraniumops.governor :as governor]))

(def read-ops #{})
(def write-ops governor/allowed-ops)

;; NOTE the invariant: `:flag-radiological-concern` and
;; `:coordinate-shipment` are members of `write-ops` (governor-gated
;; like any write) but are NEVER members of any phase's `:auto` set
;; below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops
  allowed to auto-commit when governor-clean>}."
  {0 {:label "read-only"             :writes #{}                                                          :auto #{}}
   1 {:label "assisted-extraction"   :writes #{:log-extraction-record}                                    :auto #{}}
   2 {:label "assisted-coordination" :writes #{:log-extraction-record :schedule-mining-operation
                                               :coordinate-shipment}                                       :auto #{}}
   3 {:label "supervised-auto"       :writes write-ops
      :auto #{:log-extraction-record :schedule-mining-operation}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE
    (:phase-approval), even if the governor was clean.
  - `:flag-radiological-concern`/`:coordinate-shipment` are never
    auto-eligible at any phase, so they always escalate once the
    governor clears them (or holds if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a UraniumThoriumMiningGovernor verdict to a base disposition
  before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
