# SDET User Management API — Java

[![Java quality](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/actions/workflows/quality.yml/badge.svg)](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/actions/workflows/quality.yml)
[![Java API](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/actions/workflows/api.yml/badge.svg)](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/actions/workflows/api.yml)

Java test infrastructure for the external Docker challenge API. Migrated from
[TypeScript v1.0.0](https://github.com/Boka-Rafaeli/sdet-user-management-api-typescript/tree/v1.0.0),
commit `d21686120c38aa6292d6370dc0bfe09aeff039f1`. The supplied OpenAPI bytes are unchanged.
No Python, Node.js, global Maven, Spring or application server implementation is needed.

## Run everything

Install **Eclipse Temurin 25.0.4.1+1-LTS**, select it with `JAVA_HOME`, and start Docker.
The pinned application is Linux amd64; Docker Desktop emulation is used on Apple Silicon.

```sh
git clone https://github.com/Boka-Rafaeli/sdet-user-management-api-java.git
cd sdet-user-management-api-java
./mvnw -B verify -Pfull
```

Maven Wrapper downloads Maven 3.9.11 and verifies its SHA-256. The full command runs
format/compiler/static checks, resolved dependency hash checks, unit and infrastructure
checks, then deterministic and generated API checks in fresh containers for dev, prod
and isolation. All API scopes are attempted after an independent failure. Tests use one
worker, no retries, random owned fixture IDs and dynamically allocated localhost ports.

The baseline result is **dev 35 PASS / 20 XFAIL; prod 38 PASS / 17 XFAIL; isolation 1 PASS**.
[Per-ID parity](docs/api-parity-results.json) records all 111 outcomes. XFAIL requires
an exact known status/body/state signature. Fixed, changed, skipped, missing, setup,
transport and teardown failures block execution. See [BUGS.md](BUGS.md).

## Commands

```sh
# Quality and infrastructure only (Docker is required for lifecycle probes).
./mvnw -B verify

# Baseline scope, with independent trace flags.
./mvnw -q exec:java -Dexec.mainClass=sdet.Main \
  -Dexec.args='dev --baseline --http-trace --contract-trace'

# Strict mode: deliberately exits nonzero on the application defects.
./mvnw -q exec:java -Dexec.mainClass=sdet.Main -Dexec.args='prod'

# Generated-only exploration; API findings are informational, engine errors are fatal.
./mvnw -q exec:java -Dexec.mainClass=sdet.Main \
  -Dexec.args='generated-dev --seed 424242 --examples 20 --max-failures 20 --shrink-limit 30 --record-limit 2000'

# Replay a retained request/minimal counterexample in a fresh container.
./mvnw -q exec:java -Dexec.mainClass=sdet.Main \
  -Dexec.args='generated-dev --replay reports/sample/dev/generated/events.ndjson --replay-index 0'

# Independently scrub and verify all local retained reports.
./mvnw -q exec:java -Dexec.mainClass=sdet.Main -Dexec.args='scrub'
```

Run `./mvnw compile` first when invoking `exec:java` in a new checkout.
`AUTH_TOKEN` comes from the environment, defaulting to the challenge's public local token.
Never pass real credentials on a command line. CLI settings override environment settings.
Supported settings: `BASE_URL`, `TEST_ENV`, `HTTP_TIMEOUT_SECONDS`, `HTTP_TRACE=1`,
`CONTRACT_TRACE=1`, `KNOWN_BUGS_AS_XFAIL=1`; flags include `--base-url`, `--environment`,
`--timeout`, `--baseline`, `--http-trace`, `--contract-trace`.

An explicit `BASE_URL`/`--base-url` uses an existing **disposable authorized API** for an
individual scope. `verify` always provisions fresh pinned containers. Existing-target
reports say `external-unverified`; they do not assert image identity. The selected scope
sets dev/prod. Generated options also include `--schema`, `--replay`, `--replay-index`.
The artifact gate rejects unsupported files, so do not mix unrelated files into reports.

## Reports and evidence

- `reports/run/<scope>/deterministic/`: portable HTML, JUnit XML and JSON summary.
- `reports/run/<scope>/generated/`: versioned NDJSON, JUnit XML and JSON counters/limits.
- `reports/run/<scope>/`: image digest, sanitized container logs and cleanup outcome.
- `target/site/jacoco/index.html`: coverage used to locate untested infrastructure branches.
- [Verified Java samples](reports/sample) and [their provenance](reports/sample/provenance.json).
- [CI evidence](docs/CI_EVIDENCE.md), [commit index](docs/COMMIT_INDEX.md),
  [infrastructure purpose matrix](docs/infrastructure-parity.json),
  [architecture and decisions](docs/ARCHITECTURE.md), [testing strategy](TEST_STRATEGY.md).

XFAIL is a successful JUnit testcase with explicit `outcome=XFAIL` and `bug` properties;
it is not an ordinary skipped test. Generated JUnit failures are informational API findings;
generated engine failures remain blocking. Counts alone are not an acceptance oracle.
A finite failure/record budget is an explicitly reported stop; inspect per-operation phase
counters and `stopReason`, not only process exit status.

## Manual exploration and recovery

[Postman collection, environments and cURL requests](manual/postman/README_RU.md) retain the
reference's manual capabilities and original source labels. Automated scenario IDs map in
[the manifest](docs/api-manifest.json). [Generated categories](docs/GENERATED_CHECK_PARITY.md)
explain all 13 categories, including explicit inapplicability reasons.

SIGTERM/shutdown cleanup is tested in a separate JVM. SIGKILL cannot run a shutdown hook.
For recovery, list `docker ps -a --filter label=sdet-java-owned=true`, verify the exact owned
container ID and remove that ID with `docker rm --force <id>`. CI persists its ID in
`.runtime/<scope>.json` and verifies the ownership label before removal. Never prune unrelated
containers. See [SECURITY.md](SECURITY.md) for evidence and supply-chain boundaries.
