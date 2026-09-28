# Migration parity

The source is TypeScript v1.0.0, `d21686120c38aa6292d6370dc0bfe09aeff039f1`.
The TypeScript reference was reinstalled in a clean checkout and rerun in its own fresh
containers. Java used separate fresh containers with the same immutable image digest.

[api-parity-results.json](api-parity-results.json) compares every ID: 55 dev, 55 prod and
one isolation, with no missing or changed outcome. Both yield 35/20 and 38/17 PASS/XFAIL,
plus the passing isolation case. The immutable manifest is checked before JUnit execution.

[infrastructure-parity.json](infrastructure-parity.json) inventories all 128 executed source
unit cases and 10 infrastructure cases from fresh JUnit XML, with Java evidence paths.
Java grouping and count differ: 710 email-corpus values are individually reported dynamic tests.
The matrix records purpose coverage, not a claim that the runners collect identical test counts.

| Strengthening | Java evidence |
|---|---|
| U01: atomic evidence and encodings | EvidenceTest, AdvancedInfrastructureTest, TraceTest |
| U02: exact transport | ClientTest, AdvancedInfrastructureTest |
| U03: settings boundaries | SettingsTest, AdvancedInfrastructureTest |
| U04: schema/email/no coercion | ContractTest, EmailFormatsTest, AdvancedInfrastructureTest |
| U05: resource lifecycle | GeneratedResourcesTest, OwnedUsersTest |
| U06: independent generation/replay | GenerationTest, FuzzingTest, PlatformTest, ExplorationTest |
| I01: actual runner/process/report failures | RunnerTest, BaselineTest |
| I02: actual CI branches | CiLifecycleTest, docs/ci-probes.json |

Java API shapes make mutually exclusive JSON/raw bodies a compile-time choice. Immutable
settings require no process-environment mutation/restoration. Diagnostic JsonNode cycles and
shared values have explicit witnesses. The Java evidence gate is stricter about an entirely
empty artifact directory: it blocks upload; an empty NDJSON file is allowed. These adaptations
preserve the relevant safety purpose. New persisted-state checks execute outside defect gates.
