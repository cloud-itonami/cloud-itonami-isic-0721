(ns uraniumops.governor
  "UraniumThoriumMiningGovernor -- the independent compliance layer
  that earns the UraniumOpsAdvisor the right to commit. The advisor
  has no notion of whether a site is actually registered and its
  radiological/mining permit independently verified, whether its own
  proposed `:effect` secretly claims a direct actuation instead of a
  mere proposal, or whether it has silently drifted into a permanently
  out-of-scope decision area, so this MUST be a separate system able
  to *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- OPERATIONS COORDINATION
  only (extraction-record logging, mining/haulage-operation
  scheduling, radiological-concern flagging, outbound ore/concentrate-
  shipment coordination) -- across BOTH ISIC-0721 extraction-method
  families (conventional open-pit/underground mining and in-situ
  recovery / ISR wellfields) and BOTH ores the class covers (uranium
  and thorium). It NEVER performs or authorizes:
    - direct mining-equipment control (drill-and-blast sequencing,
      haul-truck/shaft-hoist dispatch, continuous-miner operation --
      conventional mining -- or wellfield injection/extraction well-
      pump control, ion-exchange/elution circuit control -- in-situ
      recovery -- or the downstream mill circuit that turns either
      method's output into a shippable concentrate: leach/solvent-
      extraction circuit control, yellowcake precipitation/drying/
      calcining control)
    - radiological-safety decisions (exposure-limit override,
      tailings-containment-system override, ventilation/shielding
      control decisions)
    - radiation-safety-certification-authority decisions (radioactive-
      material license issuance, license suspension, compliance
      enforcement)

  Three HARD checks, ALL permanent, un-overridable by any human
  approval:

    1. Mine-site/permit unverified -- the target site record must
                                       exist AND be independently
                                       confirmed `:registered?` AND
                                       `:permit-verified?` in the store
                                       before ANY proposal for it may
                                       commit or even escalate. Never
                                       trusts a proposal's own claim
                                       about the site -- re-derived
                                       from the site's own store
                                       record, the same 'ground truth,
                                       not self-report' discipline
                                       every sibling actor's governor
                                       uses.
    2. Effect not :propose        -- every proposal's `:effect` MUST
                                      be `:propose`. Any other effect
                                      value is, by construction, a
                                      claim to directly actuate/commit
                                      outside governance -- HARD block,
                                      not merely low-confidence.
    3. Scope exclusion            -- ANY proposal (regardless of op)
                                      whose op, rationale, summary,
                                      citations or draft value touches
                                      mining-equipment-control/
                                      radiation-safety(-certification-
                                      authority) decision territory is
                                      a HARD, PERMANENT block -- this
                                      actor's charter excludes that
                                      territory structurally, not as a
                                      rollout milestone. Evaluated
                                      UNCONDITIONALLY on every
                                      proposal, the same 'exercise the
                                      failure mode directly' discipline
                                      every sibling actor's own
                                      unconditional-evaluation checks
                                      establish. An op outside the
                                      closed four-op allowlist is the
                                      SAME failure mode (an advisor
                                      proposing something it was never
                                      authorized to propose) and is
                                      folded into this same check.

  Two ESCALATE (SOFT) ops, both ALWAYS: `:flag-radiological-concern`
  (a radiological-safety observation always needs a human to look at
  it) and `:coordinate-shipment` (ore/concentrate leaving a uranium/
  thorium mine is IAEA-safeguarded nuclear material, so shipment
  coordination itself always needs human sign-off -- distinct from
  most other coordination actors in this fleet whose shipment op does
  NOT always escalate). Plus the ordinary confidence floor. All three
  escalate regardless of how clean the proposal otherwise is.
  `uraniumops.phase` independently agrees: neither op is ever a member
  of any phase's `:auto` set either -- two layers, not one."
  (:require [clojure.string :as str]
            [uraniumops.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a
  scope violation by construction (see `scope-exclusion-violations`)."
  #{:log-extraction-record :schedule-mining-operation
    :flag-radiological-concern :coordinate-shipment})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not.
  `:coordinate-shipment` is included here (unlike most sibling
  shipment-coordination ops in this fleet) because uranium/thorium
  ore and concentrate are IAEA-safeguarded nuclear material."
  #{:flag-radiological-concern :coordinate-shipment})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- direct mining-equipment
  control (either extraction method family, plus the downstream mill
  circuit) or radiation-safety(-certification-authority) decisions.
  Scanned across the proposal's op/summary/rationale/cites/value,
  never trusting the advisor's own framing of its intent."
  ["drill-and-blast" "ドリルアンドブラスト" "発破"
   "drilling pattern" "drilling-pattern" "ドリリングパターン"
   "haul truck dispatch" "haul-truck dispatch" "積込運搬機ディスパッチ" "運搬機ディスパッチ"
   "shaft hoist control" "shaft-hoist control" "立坑巻上機制御"
   "continuous miner" "continuous-miner" "連続採掘機"
   "wellfield injection" "well-field injection" "ウェルフィールド圧入" "圧入井"
   "wellfield extraction well" "ウェルフィールド採取井" "採取井制御"
   "ion exchange circuit control" "ion-exchange circuit control" "イオン交換工程制御"
   "elution circuit control" "溶離工程制御"
   "leach circuit control" "leach-circuit control" "浸出工程制御"
   "solvent extraction control" "solvent-extraction control" "溶媒抽出制御" "sx-ex control"
   "yellowcake precipitation" "イエローケーキ沈殿"
   "calciner control" "calcining control" "焙焼制御"
   "mill circuit control" "mill-circuit control" "製錬工程制御" "製錬回路制御"
   "crusher control" "破砕機制御"
   "exposure limit override" "exposure-limit override" "被ばく限度override" "被ばく限度の変更"
   "tailings containment override" "tailings-containment override" "テーリング封じ込めoverride"
   "ventilation control decision" "shielding control decision" "換気制御決定" "遮蔽制御決定"
   "radioactive material license" "license issuance" "許可発行" "放射線許可発行"
   "license suspension" "license-suspension" "免許停止"
   "compliance enforcement" "compliance-enforcement" "コンプライアンス執行"])

;; ----------------------------- checks -----------------------------

(defn- mine-site-unverified-violations
  "The target mine site must exist AND be independently
  `:registered?`/`:permit-verified?` in the store -- never trust the
  proposal's own `:site-id` claim without a store lookup."
  [{:keys [site-id]} st]
  (let [s (store/site st site-id)]
    (when-not (and s (:registered? s) (:permit-verified? s))
      [{:rule :mine-site-unverified
        :detail (str site-id " は未登録または鉱区/放射線許可が未検証のsite -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim
  to directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist,
  or one whose content touches mining-equipment-control/radiation-
  safety(-certification-authority) territory, regardless of confidence
  or how clean every other check is. Evaluated UNCONDITIONALLY on
  every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "採掘設備制御(発破/掘進順序/ウェルフィールド・製錬工程制御等)または放射線安全(認可機関)の判断領域に触れる提案は永久に禁止"}])))

(defn check
  "Censors a UraniumOpsAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [site-id (or (:site-id proposal) (:site-id request))
        hard (into []
                   (concat (mine-site-unverified-violations {:site-id site-id} store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (always-escalate-ops (:op proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :site-id    (:site-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
