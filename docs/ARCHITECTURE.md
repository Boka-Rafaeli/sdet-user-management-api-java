# Architecture and decisions

The external application is unchanged. `Settings` is immutable; `ApiClient` owns an HTTP/1.1
client behind an injectable transport. Redirects are disabled; raw bytes and absent body are
separate from JSON null. Encoding operates on UTF-8 path-segment bytes, never form encoding.

`Contract` resolves local OpenAPI 3.0 references, rejects cycles/external references and adapts
only the supported schema subset to explicit JSON Schema Draft 4. networknt validates types,
required fields and bounds without coercion or defaults. Separate recursive format assertions
use the Python-compatible contains-`@` deterministic oracle or the generated email oracle.
The latter uses ICU UTS46 and the copied 710-value jsonschema_rs corpus with provenance.
Passing a finite corpus does not establish universal RFC email equivalence.

`Scenarios` builds 55 curated cases and one isolation case. `SuiteRunner` executes actual
Jupiter dynamic tests through JUnit Platform. Only `Baseline.KnownDefect` after exact signature
matching becomes XFAIL. `Baseline.execute` runs cleanup before assigning XFAIL; cleanup failure
wins. Manifest integrity and every executed outcome are checked. Missing/duplicate collection
also produces a failing JUnit testcase. Unrelated exceptions remain operational failures.

`Generation` preflights the current OpenAPI subset and derives examples/boundaries. It rejects
new security schemes, response headers, non-JSON media, unsupported composition/patterns and
undeclared path parameters. `GeneratedChecks` accounts for all 13 source categories.
`Fuzzing` uses actual jqwik arbitraries, random generators and shrink trees. A jqwik session
stays open through lazy value evaluation and shrinking; generation is serial. Replay restores
Authentication from the environment and prepares fresh owned resources. Version, seed, limits,
operation, phase, original/minimal request and shrink attempts are retained.

`GeneratedResources` resets/creates/reads valid-path PUT/DELETE fixtures. Negative bodies and
paths stay negative. Generated cleanup ignores transport errors and DELETE statuses as in the
reference, but exposes interruption/programming errors. Curated cleanup propagates failures.
`Exploration` keeps API findings separate from engine failures and records explicit budget stops.
Response evidence is bounded to 16,384 characters; truncation is marked. Record and shrink limits
are independent of per-operation fuzz example limits. Unsupported schema fails before requests.

`Redaction` sanitizes producers; `Evidence` independently parses, atomically scrubs NDJSON and
verifies allowed retained formats. `Verification` runs every stage despite earlier failures.
`CiMain` exposes the same stages to GitHub Actions; the upload condition depends on scrub success.

## ADR-001: runner and dependency selection

Pinned Eclipse Temurin 25.0.4.1+1-LTS, Maven 3.9.11, JUnit 6.0.1, jqwik 1.9.3, Jackson 2.18.3,
networknt 1.5.9 and ICU4J 77.1 passed real JVM and Linux CI checks. There was no need to fall back
to JUnit 5. `PlatformTest` proves Jupiter/jqwik execution, seeded failure and shrinking in child
JVMs. `RunnerTest` separately proves exact/strict/unaffected/fixed/changed/network/async/setup/
cleanup/secret/skip/empty/duplicate/missing outcomes and reports.

The Adoptium API identifies this emergency patch as SemVer `25.0.4+101.0.LTS`; setup-java needs
that form. `BootstrapTest` asserts the actual runtime `25.0.4.1+1-LTS`, so a different resolved
patch cannot silently pass. Wrapper distribution SHA-256, wrapper files and resolved application
JARs are checked. All direct dependencies/plugins are exact-pinned; `dependencyConvergence` and
`requireReleaseDeps` enforce the application graph. Plugin transitive inventory is recorded
separately and is not misrepresented as an npm-style universal lockfile.

## ADR-002: report and generator equivalence

Java uses JUnit properties for XFAIL rather than skipped/aborted results. This avoids treating an
ordinary JUnit assumption abort as an expected defect. The generator preserves check purposes,
phases, limits and resource behavior, not TypeScript fast-check random sequences or Schemathesis
NDJSON format. Schema and engine versions must match for seed replay. A serialized minimal
request is the explicit cross-process replay format. A stopped bounded run reports its reason
and counters; it is not evidence that every phase ran to completion.

## Evidence-driven corrections

The first Linux cold pull exposed stderr progress corrupting parsed Docker stdout. Streams are
now independently drained; only `docker logs` combines them. Local cached images would not have
proved this path. Atomic scrub tests also verify POSIX mode preservation, temporary-path ownership,
malformed UTF-8 and directory links. JSON diagnostic cycle handling is tested separately from
valid JSON serialization. The implementation index records each additional correction.

Sources: [JUnit](https://docs.junit.org/6.0.1/overview.html),
[jqwik lifecycle](https://jqwik.net/docs/1.9.3/user-guide.html),
[networknt](https://github.com/networknt/json-schema-validator/tree/1.5.9),
[JDK HttpClient](https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpClient.html).
