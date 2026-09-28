# Real CI execution evidence

Normal API matrix and quality passed on `4d9fed8f7aaa3c3648ce7b48624182e41f21d82f`:
[API matrix](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/actions/runs/36373742892),
[quality](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/actions/runs/36373742893).
These are intermediate implementation results; the release links its own exact-SHA CI runs.

| Controlled probe | Observed behavior | Run |
|---|---|---|
| Deterministic / dev | Deterministic failed; generator, cleanup and scrub succeeded; job failed | [36373811192](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/actions/runs/36373811192) |
| Generator / prod | Missing schema caused engine failure; cleanup and scrub succeeded; job failed | [36373813465](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/actions/runs/36373813465) |
| Scrub / isolation | Malformed NDJSON failed scrub; upload skipped; cleanup succeeded | [36373816260](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/actions/runs/36373816260) |

Exact step outcomes and commit SHAs are retained in [ci-probes.json](ci-probes.json).
Intentional probes are red by design; a green probe would fail the acceptance audit.
Generated totals in sample evidence belong only to the run identified by its provenance.
Final clean-clone and release evidence are recorded in `ACCEPTANCE.md` when verified.
