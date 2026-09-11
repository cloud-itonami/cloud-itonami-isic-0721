(ns uraniumops.store
  "SSoT for the ISIC-0721 uranium/thorium-ore mining OPERATIONS-
  COORDINATION actor, behind a `Store` protocol so the backend is a
  swap, not a rewrite -- the same seam every `cloud-itonami-isic-*`
  actor in this fleet uses.

  This actor coordinates the BACK OFFICE of a uranium/thorium-ore
  mine site. ISIC 0721 covers more than one extraction method, and
  this store's demo data seeds a site of each kind deliberately, plus
  BOTH ores the class covers (uranium and thorium), so the actor is
  never accidentally coupled to a single method or a single ore:

  - Conventional (open-pit/underground, hard-rock) mining -- drill-
    and-blast, haul-truck/shaft-hoist material movement, then
    crushing/grinding/leaching/solvent-extraction/ion-exchange milling
    to a yellowcake (U3O8) concentrate.
  - In-situ recovery (ISR/ISL) -- wellfield injection/extraction wells
    leach the ore body in place; the pregnant liquor is pumped to an
    ion-exchange/elution circuit, never touching a drill-and-blast/
    haul-truck step at all.

  It never touches mining-equipment control (either method's
  extraction/haulage machinery, or the downstream mill/wellfield
  processing circuits that turn ore into a shippable concentrate) or
  any radiation-safety-certification-authority decision (radioactive-
  material license issuance, license suspension, compliance
  enforcement) -- see `uraniumops.governor`'s `scope-exclusion-
  violations`, a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/
  demo (no deps). A `sites` directory keyed by `:site-id` STRING
  (never a keyword -- `cloud-itonami-isic-0510`'s own prior scaffold
  attempt keyed the seed map with keyword site-ids while every lookup
  used the string `:site-id` off the proposal, so `(get sites
  site-id)` silently missed on every call and masked itself as HARD
  site-unverified holds across 10 assertions; avoided here by keying
  consistently on the string from the start).

  A registered mine-site record AND an independently-verified
  radiological/mining permit must exist before ANY proposal for that
  site may ever commit or escalate -- `uraniumops.governor`'s
  `mine-site-unverified-violations` re-derives this from the site's
  own `:registered?`/`:permit-verified?` fields, never from proposal
  self-report, the SAME 'ground truth, not self-report' discipline
  every sibling actor's own governor uses.

  The ledger stays append-only: which site a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by
  whom is always a query over an immutable log.")

(defprotocol Store
  (site [s site-id] "Registered uranium/thorium mine-site record, or nil.
    Site map: {:site-id .. :name .. :ore .. :method .. :registered? bool :permit-verified? bool}.")
  (all-sites [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-sites [s sites] "replace/seed the site directory (map site-id->site)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained site directory covering both the happy path
  and the governor's own hard checks, so the actor + tests run
  offline. Deliberately spans both ISIC-0721 extraction methods
  (conventional and in-situ recovery) AND both ores the class covers
  (uranium and thorium), so no test or demo scenario is accidentally
  coupled to a single method or ore."
  []
  {:sites
   {"uth-site-1" {:site-id "uth-site-1" :name "Northern Conventional Uranium Mine"
                   :ore :uranium :method :conventional :registered? true :permit-verified? true}
    "uth-site-2" {:site-id "uth-site-2" :name "Eastern In-Situ Recovery Uranium Wellfield"
                   :ore :uranium :method :in-situ-recovery :registered? true :permit-verified? true}
    "uth-site-3" {:site-id "uth-site-3" :name "Southern Thorium (Monazite Sands) Mine (permit lapsed)"
                   :ore :thorium :method :conventional :registered? true :permit-verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (site [_ site-id] (get-in @a [:sites site-id]))
  (all-sites [_] (sort-by :site-id (vals (:sites @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-sites [s sites] (when (seq sites) (swap! a assoc :sites sites)) s))

(defn seed-db
  "A MemStore seeded with the demo site directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `sites` map (site-id string ->
  site map) -- the primary test/dev entry point. `sites` may be empty
  (an unregistered-everywhere store)."
  [sites]
  (->MemStore (atom {:sites (or sites {}) :ledger [] :coordination-log []})))
