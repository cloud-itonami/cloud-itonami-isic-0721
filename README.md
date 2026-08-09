# cloud-itonami-isic-0721

Open Business Blueprint for **ISIC Rev.4 0721**: Mining of uranium and
thorium ores — an ISIC Wave 3 (production/mining) operations-
coordination actor per ADR-2607121000. Back-office and coordination
workflow for uranium/thorium-ore mine sites, modeled closely on
`cloud-itonami-isic-0893`'s (Extraction of salt) governed-actor
discipline.

**Maturity: `:implemented`** — UraniumOpsAdvisor ⊣
UraniumThoriumMiningGovernor as a langgraph-clj StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt). All source `.cljc` (portable to JVM / ClojureScript /
GraalVM), no JVM-only interop.

## Scope note: this is a civilian raw-material mining actor

This actor covers **ISIC 0721 only**: the extraction of uranium- and
thorium-bearing ore from the ground and its coordination through to a
shippable concentrate. It is a nuclear-fuel-cycle *raw-material
mining* classification, radiation-safety-regulated like any other
radioactive-ore extraction activity — it is **not** weapons-related
and has no connection to enrichment, fuel fabrication, or any
weapons-adjacent activity (those are separate ISIC classes this fleet
deliberately excludes, e.g. 2520/3040). See `90-docs/adr/` in the
`com-junkawasaki` superproject for the coverage decision record.

## Scope: both ISIC-0721 extraction methods

ISIC 0721 covers more than one extraction method, and this actor
coordinates the back office of either kind of site:

- **Conventional (open-pit/underground, hard-rock) mining** —
  drill-and-blast/continuous-miner extraction, haul-truck or
  shaft-hoist material movement, feeding a mill (crushing, grinding,
  leaching, solvent extraction, ion exchange) that produces a
  yellowcake (U3O8) concentrate.
- **In-situ recovery (ISR/ISL)** — wellfield injection/extraction
  wells leach the ore body in place; the pregnant liquor is pumped to
  an ion-exchange/elution circuit, never touching a drill-and-blast/
  haul-truck step at all.

The actor is deliberately method-agnostic at the coordination layer
(extraction-record logging, mining-operation scheduling, radiological-
concern flagging, shipment coordination apply to either method); its
governor's scope exclusions cover both methods' equipment-control
territory explicitly (see below).

## CRITICAL: Scope Exclusions

This actor **DOES NOT** and **NEVER WILL**:

- **Direct mining-equipment control** — drill-and-blast sequencing,
  haul-truck/shaft-hoist dispatch, continuous-miner operation
  (conventional mining) — or wellfield injection/extraction well-pump
  control, ion-exchange/elution circuit control (in-situ recovery) —
  or the downstream mill circuit that turns either method's output
  into a shippable concentrate (leach/solvent-extraction circuit
  control, yellowcake precipitation/drying/calcining control)
- **Radiological-safety decisions** — exposure-limit overrides,
  tailings-containment-system overrides, ventilation/shielding control
  decisions
- **Radiation-safety-certification-authority decisions** —
  radioactive-material license issuance, license suspension, or
  compliance enforcement

This actor **only** coordinates back-office operations:
extraction-record logging (ore tonnage/grade assay), mining/haulage-
operation scheduling, radiological-concern flagging (radiation
exposure, tailings-containment integrity — always routed to a human),
and outbound ore/concentrate-shipment coordination. Every proposal the
advisor drafts carries `:effect :propose` — never a direct actuation —
and `uraniumops.governor` independently re-scans every proposal's
content for the excluded scope areas above, regardless of op or
confidence.

## Operations

Closed proposal-op allowlist (`uraniumops.governor/allowed-ops`), all
`:effect :propose`:

- `:log-extraction-record` — ore tonnage/grade-assay data logging
- `:schedule-mining-operation` — extraction/haulage-operation
  scheduling proposal
- `:flag-radiological-concern` — surface a radiation-exposure/
  tailings-containment concern — **ALWAYS escalates**
- `:coordinate-shipment` — outbound ore/concentrate-shipment
  coordination — **ALWAYS escalates**: this is IAEA-safeguarded
  nuclear material, distinct from most sibling shipment-coordination
  ops in this fleet, which do not always escalate

**HARD invariants** (always `:hold`, never human-overridable):

1. **Mine-site/permit unverified** — the target site record must
   exist AND be independently `:registered?`/`:permit-verified?` in
   the store before any proposal for it may commit or even escalate.
2. **Effect not `:propose`** — any proposal whose `:effect` is not
   `:propose` is, by construction, a claim to directly actuate outside
   governance.
3. **Scope exclusion** — any proposal (regardless of op) outside the
   closed allowlist, or whose rationale/summary/citations/value
   touches mining-equipment-control (either method family, or the
   downstream mill circuit) or radiation-safety(-certification-
   authority) territory, is a permanent, un-overridable block.
   Evaluated unconditionally on every proposal.

**ESCALATE** (always human sign-off, when the governor is otherwise clean):

- `:flag-radiological-concern` — always, regardless of confidence.
- `:coordinate-shipment` — always, regardless of confidence (IAEA
  safeguards).
- Low advisor confidence (`< 0.6`).

## Rollout phases (`uraniumops.phase`)

Phase 0 (read-only) → 1 (extraction-record logging, approval-gated) →
2 (adds mining-operation scheduling + shipment coordination, approval-
gated) → 3 (supervised auto: extraction-record logging/mining-
operation scheduling may auto-commit when governor-clean and
confident). `:flag-radiological-concern` and `:coordinate-shipment`
are deliberately absent from every phase's `:auto` set — a permanent
structural fact, not a rollout milestone still to come — matching
`uraniumops.governor`'s own `always-escalate-ops` independently.

## Development

```bash
clojure -M:test   # run the full suite
clojure -M:run    # walk the demo scenarios (uraniumops.sim)
clojure -M:dev:render-html  # REAL actor -> docs/samples/operator-console.html
clojure -M:lint    # clj-kondo
```

AGPL-3.0-or-later, forkable by any qualified operator. Part of cloud-itonami.
