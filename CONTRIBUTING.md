# Contributing to cloud-itonami-isic-0721

Contributions should preserve the actor's scope: back-office coordination only,
with CRITICAL exclusions of direct mining-equipment control, radiological-
safety decisions, and radiation-safety-certification-authority decisions
(see README.md).

- All code must be .cljc (portable Clojure, no JVM-only constructs).
- Tests must pass: kbb -M:test
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Direct mining-equipment control — drill-and-blast sequencing,
  haul-truck/shaft-hoist dispatch, continuous-miner operation
  (conventional mining), or wellfield injection/extraction well-pump
  control, ion-exchange/elution circuit control (in-situ recovery), or
  the downstream mill circuit (leach/solvent-extraction, yellowcake
  precipitation/drying/calcining control).
- Radiological-safety decisions (exposure-limit overrides, tailings-
  containment-system overrides, ventilation/shielding control decisions).
- Radiation-safety-certification-authority decisions (license issuance,
  license suspension, compliance enforcement).

Contributions that cross these boundaries will be rejected.
