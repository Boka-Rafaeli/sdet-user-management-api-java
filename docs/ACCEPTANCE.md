# Java migration acceptance

All 28 planned functional stages are implemented, with additional small commits for observed
CI, evidence and coverage issues. Changes were committed only after the available cumulative
checks passed in a fresh export. Source Python and TypeScript repositories were not modified.

## Verified inputs and outcomes

- Reference: TypeScript v1.0.0 / `d21686120c38aa6292d6370dc0bfe09aeff039f1`, freshly installed
  and rerun: all 128 unit and 10 infrastructure cases passed.
- Every deterministic ID matched in separate fresh containers: dev 35 PASS/20 XFAIL,
  prod 38 PASS/17 XFAIL, isolation 1 PASS. All used the pinned digest in `provenance.json`.
- `Parity` is an executable Java comparator. It rejects changed status, wrong digest,
  missing/duplicate IDs, skipped outcomes and FAIL. Independent mismatch witnesses are tests.
- Five generated operations and all three phases executed under default limits; all 13
  categories are represented, including four explicit inapplicability reasons. jqwik seed,
  shrinking, serialized replay, negative input preservation and fixture failures were checked.
- Runner subprocess probes cover exact/strict/unaffected/fixed/changed/network/async/setup/
  cleanup/secret/skip/empty/duplicate/missing behavior. Their reports and process exits agree.
- Producer redaction and independent evidence gate passed. Corrupt data and injected file
  failures block publishing; symlinks and unsupported formats are rejected.

## Clean installation and CI

A new GitHub clone at `0fae6e631b3e0bbf8ff9c40acc9cfeb5bce1e442` installed into an empty Maven
user home and repository and completed `./mvnw -B verify -Pfull`: 792 quality/infrastructure
cases passed, then all API scopes and exploration passed their execution gates. The final
parity verifier adds another infrastructure test. [Machine-readable evidence](clean-clone-evidence.json)
records exact counts and the installation log checksum. The final release is checked again from
its own clean clone; final-SHA logs/results are distributed as release assets.

Normal Linux quality and API matrix runs and five intentional failure probes are documented in
[CI_EVIDENCE.md](CI_EVIDENCE.md) and [ci-probes.json](ci-probes.json). Deterministic failure did
not suppress generation; engine failure blocked the job; scrub failure skipped upload in dev,
prod and isolation; cleanup succeeded in every probe. The final exact SHA and green CI links
are recorded in the [v1.0.0 release](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/releases/tag/v1.0.0),
which is published only after the final validation succeeds.

[Coverage review](coverage-review.json) includes instrumented subprocesses. Baseline classification
and reports reached all measured branches; other classes retain documented unvisited branches.
A single coverage percentage or equal test count is not used as a correctness claim.

## Reproduce parity

After rerunning the reference in its own containers and Java via the full command:

```sh
./mvnw -q exec:java -Dexec.mainClass=sdet.Parity \
  -Dexec.args='/absolute/reference/reports/generated reports/run parity.json'
```

The comparator uses the source ID manifest, validates digest identity and compares each outcome.
The supplied samples retain their actual earlier implementation SHA, rather than claiming to
have been produced from a future release commit. Finite email-corpus parity is not universal RFC
proof. Exact generated random sequences differ from fast-check/Schemathesis. SIGKILL recovery is
manual by owned ID; no Java shutdown hook can execute after SIGKILL.
