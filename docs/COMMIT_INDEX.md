# Commit index

Each row is an actual Git commit. Each implementation step was cumulatively verified in a fresh index export before commit; see IMPLEMENTATION_STEPS.md for commands and timestamps. CI-specific validation necessarily follows pushing its implementation commit. No future SHA is invented.

| SHA | Function |
|---|---|
| [9fccc4e6](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/9fccc4e6e7b280c6552cb66754181b63d5b92413) | docs(plan): define Java migration scope and parity matrix |
| [46843ea5](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/46843ea535047f8cb0c2ed75c9a0b5f5ad4eef10) | build: bootstrap pinned Java and Maven toolchain |
| [714bb56e](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/714bb56e1979cc408fc21d7af8f54d7672f98433) | test(platform): prove runner and property engine compatibility |
| [ba14e312](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/ba14e3126205732ff9411fe52cf307a797c0a259) | feat(config): add environment settings and CLI options |
| [34a7fed6](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/34a7fed6115161f73338ba8c56545edd04a687d8) | feat(client): implement exact API transport semantics |
| [02904b60](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/02904b60d099380bfab3b07cb1cf3003c8301484) | feat(contract): validate OpenAPI response contracts |
| [e17ad3a1](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/e17ad3a110533a0efe8318a26375c94b2941079e) | feat(contract): preserve deterministic and generated email formats |
| [f8d4b088](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/f8d4b0889f50e277d60190183a8d80729e71244e) | feat(runtime): manage isolated Docker lifecycle |
| [0db170e9](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/0db170e95e0b44f64e6ca1865b87e957783a9fa8) | feat(fixtures): manage owned users and cleanup |
| [73503aaa](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/73503aaada506f50823605222eca8a942af98018) | feat(baseline): classify exact known-defect signatures |
| [84d37b6d](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/84d37b6df527f024926e1a3c92aab8c7250f0dd6) | feat(reports): emit complete and safe execution reports |
| [9e190633](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/9e1906336e2a2f7f42d2d7c9f781a86a62abbb37) | test(crud): cover user lifecycle and conflicts |
| [33a63829](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/33a638295361def499c702ed517438872c44a726) | test(update): verify persistence and email changes |
| [788b7026](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/788b70268f98205e5109787dc42ba0ed5517fcd3) | test(validation): cover POST schema boundaries |
| [4bceb2ef](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/4bceb2efda50d175f4b60f91a4a09d5a105d98a1) | test(validation): cover PUT schema boundaries |
| [4e8dbd52](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/4e8dbd528e28c6c99932e34d2afc78f15bbcbe97) | test(protocol): cover raw bodies and encoded paths |
| [5e5df8d8](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/5e5df8d8b49025c88f42a38644feb771adc471a0) | test(auth): verify DELETE authorization behavior |
| [2c74fb1c](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/2c74fb1cbcdaa689b99e95db51041f7d28b6f3a8) | test(isolation): verify dev and prod independence |
| [a15f8ae2](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/a15f8ae240c50f335431c616f89f1001ecc26537) | feat(trace): add redacted HTTP and contract diagnostics |
| [09a1c78f](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/09a1c78fb72aecf4ff83195e03918ca6fb7c13db) | feat(generation): derive examples boundaries and check catalogue |
| [3293693d](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/3293693d3b2683d74207c218c24a6dad0f601cf7) | feat(generation): add seeded fuzzing shrinking and replay |
| [0d8acd8a](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/0d8acd8a11fba42b824556c2d417c4e4c1945921) | feat(generation): provision verified resources for writes |
| [7324ed58](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/7324ed5862c9ecfcb5f988d26859ebc6ddff006a) | fix(ci): express pinned Temurin patch in Adoptium SemVer |
| [ffe2a281](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/ffe2a281f92d5d54f340ff0bcb73a8c72ad74226) | feat(generation): emit bounded exploration evidence |
| [06f56529](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/06f56529dd8a8ee1c5e94effa2f51d3d4f957b7d) | fix(runtime): keep Docker command stdout separate from pull diagnostics |
| [14e2b15c](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/14e2b15c238276403c60ff51952ce25e5ed5e524) | feat(security): scrub and verify retained evidence |
| [b26c5b16](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/b26c5b16602499c81123e9304c2e851444c4ba14) | feat(verification): run all scopes and enforce completeness |
| [4d9fed8f](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/4d9fed8f7aaa3c3648ce7b48624182e41f21d82f) | ci(api): run isolated scopes and validate failure paths |
| [aa50f81e](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/aa50f81e4d61e029b2c3329d249eb09310b179be) | build(quality): verify dependency checksums and static analysis rules |
| [e62e8276](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/e62e8276e7f4b199a5136c95659b970bf86f62e2) | test(infrastructure): strengthen malformed evidence and lifecycle witnesses |
| [e033eebb](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/e033eebbe11796858e6ddff0472e5cf215cf3fff) | feat(trace): explain ordered contract checks without exposing payloads |
| [e3608772](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/e3608772f227760671b2212ab599fae7fb2e70ac) | fix(verification): retain negative fuzzing and fail incomplete JUnit reports |
| [12ff0f5d](https://github.com/Boka-Rafaeli/sdet-user-management-api-java/commit/12ff0f5da1f9cc6792b48d5a80fc92fdcbd9ee8e) | test(runner): prove all failure modes through isolated JVM reports |
