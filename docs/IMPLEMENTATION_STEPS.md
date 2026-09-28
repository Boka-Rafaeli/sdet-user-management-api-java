# Implementation evidence

Each implementation commit is validated from a fresh exported index before commit. SHA entries refer to preceding commits; the index never predicts its own SHA.

01: Scope, exact reference SHA, immutable OpenAPI and source test inventories. Checked manifest (55 API + 1 isolation), schema SHA and reference tag.

2026-09-27T04:38:25Z: `build: bootstrap pinned Java and Maven toolchain` — fresh index export; `./mvnw -B verify` passed. API coordinator also runs when present. Previous SHA: `9fccc4e`.

2026-09-27T04:38:57Z: `test(platform): prove runner and property engine compatibility` — fresh index export; `./mvnw -B verify` passed. API coordinator also runs when present. Previous SHA: `46843ea`.

2026-09-27T04:40:58Z: `feat(config): add environment settings and CLI options` — fresh index export; `./mvnw -B verify` passed. API coordinator also runs when present. Previous SHA: `714bb56`.

2026-09-27T04:41:15Z: `feat(client): implement exact API transport semantics` — fresh index export; `./mvnw -B verify` passed. API coordinator also runs when present. Previous SHA: `ba14e31`.

2026-09-27T04:43:12Z: `feat(contract): validate OpenAPI response contracts` — fresh index export; `./mvnw -B verify` passed. API coordinator also runs when present. Previous SHA: `34a7fed`.

2026-09-27T04:43:33Z: `feat(contract): preserve deterministic and generated email formats` — fresh index export; `./mvnw -B verify` passed. API coordinator also runs when present. Previous SHA: `02904b6`.

2026-09-27T04:45:00Z: `feat(runtime): manage isolated Docker lifecycle` — fresh index export; `./mvnw -B verify` passed. API coordinator also runs when present. Previous SHA: `e17ad3a`.

2026-09-27T04:46:17Z: `feat(fixtures): manage owned users and cleanup` — fresh index export; `./mvnw -B verify` passed. API coordinator also runs when present. Previous SHA: `f8d4b08`.

2026-09-27T04:46:50Z: `feat(baseline): classify exact known-defect signatures` — fresh index export; `./mvnw -B verify` passed. API coordinator also runs when present. Previous SHA: `0db170e`.

2026-09-27T04:47:53Z: `feat(reports): emit complete and safe execution reports` — fresh index export; `./mvnw -B verify` passed. API coordinator also runs when present. Previous SHA: `73503aa`.

2026-09-27T04:49:06Z: `test(crud): cover user lifecycle and conflicts` — fresh index export; `./mvnw -B verify` passed. API coordinator also runs when present. Previous SHA: `84d37b6`.

2026-09-28T03:05:09Z: `test(update): verify persistence and email changes` — fresh index export; `./mvnw -B verify` and `exec:java -Dexec.mainClass=sdet.Main -Dexec.args="verify --baseline"` passed. Previous SHA: `9e19063`.

2026-09-28T03:06:36Z: `test(validation): cover POST schema boundaries` — fresh index export; `./mvnw -B verify` and `exec:java -Dexec.mainClass=sdet.Main -Dexec.args="verify --baseline"` passed. Previous SHA: `33a6382`.

2026-09-28T03:07:32Z: `test(validation): cover PUT schema boundaries` — fresh index export; `./mvnw -B verify` and `exec:java -Dexec.mainClass=sdet.Main -Dexec.args="verify --baseline"` passed. Previous SHA: `788b702`.

2026-09-28T03:09:11Z: `test(protocol): cover raw bodies and encoded paths` — fresh index export; `./mvnw -B verify` and `exec:java -Dexec.mainClass=sdet.Main -Dexec.args="verify --baseline"` passed. Previous SHA: `4bceb2e`.

2026-09-28T03:11:05Z: `test(auth): verify DELETE authorization behavior` — fresh index export; `./mvnw -B verify` and `exec:java -Dexec.mainClass=sdet.Main -Dexec.args="verify --baseline"` passed. Previous SHA: `4e8dbd5`.
