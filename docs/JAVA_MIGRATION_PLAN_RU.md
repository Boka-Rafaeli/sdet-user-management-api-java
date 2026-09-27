# План переноса SDET-проекта на Java

Статус: план, реализация и создание GitHub-репозитория в рамках этой задачи не начинались.
Предлагаемое имя отдельного репозитория: `sdet-user-management-api-java`.

## Цель и эталоны

Реализовать на Java весь согласованный функционал тестовой инфраструктуры: API-сценарии, OpenAPI-проверки, генерацию запросов, подготовку данных, диагностику, отчёты, очистку артефактов и CI. Сохранить пошаговую историю небольших функциональных коммитов и проверять каждый этап из чистого checkout.

Основной эталон — TypeScript v1.0.0, commit `d21686120c38aa6292d6370dc0bfe09aeff039f1`. Исходный Python-проект и независимые контрольные ответы используются для проверки спорных деталей. OpenAPI остаётся источником требований к приложению; совпадение Java и TypeScript само по себе не доказывает корректность общей проверки.

Переносится тестовая инфраструктура. Сервер остаётся внешним Docker-приложением. Java-репозиторий не требует Python или Node.js для работы. Maven Wrapper, XML, YAML, JSON и Markdown остаются в своих стандартных форматах; исполняемая логика тестов, генератора и вспомогательных утилит пишется на Java.

Сохранить:

- 55 исходных параметризованных API ID для каждого окружения и один ID изоляции.
- Все девять известных дефектов с точными status/body/state-сигнатурами и наблюдение об unsupported methods.
- Назначение всех 128 unit-тестов и 10 инфраструктурных проверок TypeScript-релиза; группировка и итоговое количество Java-тестов могут отличаться.
- Дополнительные проверки сохранённого состояния после отклонённых записей.
- Все 13 категорий исходного каталога generated checks: активные проверки и явные причины неприменимости остальных.
- Postman/cURL-возможности, переносимый HTML, JUnit, JSON-summary, versioned NDJSON, image digest и безопасные логи.

Ранее подтверждённый результат: dev — 35 PASS / 20 XFAIL; prod — 38 PASS / 17 XFAIL; isolation — 1 PASS. В Java это цель сравнения, а не уже полученный результат. Для нового сравнения заново запустить эталон и Java в разных чистых контейнерах с digest `sha256:c80c42ffafccb6ba9cd9a128421445d308225f09902c03aeadbc99e321176bbc`.

## Предлагаемый стек

| Задача | Решение | Что подтвердить до массового переноса |
| --- | --- | --- |
| Runtime | JDK 25 LTS, конкретный OpenJDK-дистрибутив и patch закрепить | Установка локально и в Linux CI |
| Сборка | Maven Wrapper; точные версии зависимостей и plugins | Проверка wrapper checksum, запрет SNAPSHOT/ranges, resolved dependency inventory |
| Runner | JUnit Jupiter + JUnit Platform; кандидат — совместимая линия JUnit 6 | jqwik, launcher, Surefire/Failsafe и lifecycle extensions должны работать совместно |
| Assertions | JUnit Assertions; дополнительные библиотеки только при реальной необходимости | Ошибки требований отделяются от инфраструктурных исключений |
| HTTP | `java.net.http.HttpClient` за собственным интерфейсом transport | Redirect.NEVER, HTTP/1.1, timeout, raw bytes, пустые заголовки, encoding |
| JSON/YAML | Jackson tree model и YAML parser совместимой версии | Не превращать негативные payload в типизированные DTO с ранней валидацией |
| Contract oracle | networknt JSON Schema validator + адаптер текущего OpenAPI 3.0 | Явный dialect, format assertions, refs и отсутствие coercion/defaults |
| Генерация | jqwik + собственный слой OpenAPI discovery/examples/coverage/checks | Seed, shrinking, replay и отдельные исходы API findings / engine errors |
| Docker | Java lifecycle manager через Docker CLI и ProcessBuilder | Владение по ID, readiness, stderr/stdout, cleanup и shutdown |
| Quality | Compiler checks, форматирование, статический анализ, JaCoCo | Покрытие критических ветвей; процент покрытия не заменяет независимые проверки |
| Reports | Собственные безопасные single-file HTML / JUnit / JSON / NDJSON | XFAIL, escaping, полнота выполнения и ограничения объёма evidence |

Точные совместимые версии фиксируются после небольшого рабочего прототипа. Не считать любую комбинацию последних версий совместимой. Если JUnit 6 и выбранная версия jqwik не проходят контрольные запуски, выбрать проверенную совместимую линию и записать причину в ADR до переноса API-сценариев.

Spring Boot для этой тестовой инфраструктуры не требуется. Генератор не заменяется одним подключением jqwik: чтение OpenAPI, категории проверок, ресурсные fixtures и отчётность реализуются отдельно.

## Ранние проверки риска

1. **Runner и XFAIL.** Провести маленькую управляемую suite через настоящий JUnit Platform и отдельный JVM-процесс. Проверить exact/changed/fixed defect, strict mode, unaffected environment, setup/transport/async/teardown failures. Только точная сигнатура становится XFAIL. Ошибка очистки имеет приоритет. Обычный skipped/aborted test не считается допустимым XFAIL. Проверять одновременно exit code, HTML, JUnit и summary.
2. **Генератор и JVM.** На маленькой схеме доказать работоспособность jqwik, воспроизводимость seed и минимизацию. Ошибки сети, setup, schema и записи отчёта не превращаются в информационные counterexamples. Проверить выполнение всех фаз и работу лимитов.
3. **Schema/email.** Раздельно сохранить обычный Python FormatChecker-compatible email oracle и более строгий generated oracle. Перенести корпус из 710 контрольных значений и его происхождение. Не считать стандартный Java email validator автоматически эквивалентным.
4. **HTTP/JSON.** Проверить реальным локальным HTTP-сервером raw body, JSON null против отсутствующего тела, пустой Authentication, запрет redirects и тайм-аут. Явно проверить UTF-8, `+`, `%`, `/`, `@` и отсутствие двойного кодирования; form-encoding не подставлять вместо path-encoding. Для JSON чисел проверить boolean, строку, дробь, целое и границы без неявного преобразования.

## План коммитов

Каждая строка — отдельный функциональный коммит с собственными проверками. Если diff получается слишком большим, этап делится дополнительно. Число 28 — ориентир, а не ограничение качества.

| № | Commit message | Результат и ключевая проверка |
| --- | --- | --- |
| 01 | `docs(plan): define Java migration scope and parity matrix` | SHA эталона, неизменный OpenAPI, IDs, матрица возможностей и критерии приёмки |
| 02 | `build: bootstrap pinned Java and Maven toolchain` | Wrapper, версии, compilation/format/static checks, минимальный quality workflow |
| 03 | `test(platform): prove runner and property engine compatibility` | Настоящие Jupiter/jqwik/launcher subprocesses; рабочая комбинация зависимостей |
| 04 | `feat(config): add environment settings and CLI options` | Immutable config, CLI/env/default precedence, некорректные значения, независимость dev/prod |
| 05 | `feat(client): implement exact API transport semantics` | Пять операций, raw requests, tokens, bytes, timeout, no redirects; unit + локальный HTTP-server |
| 06 | `feat(contract): validate OpenAPI response contracts` | Status → operation/response → media/empty body → JSON/schema; refs и schema mutations |
| 07 | `feat(contract): preserve deterministic and generated email formats` | Два независимых адаптера, 710-case corpus, numeric/boolean/coercion witnesses |
| 08 | `feat(runtime): manage isolated Docker lifecycle` | Digest, dynamic localhost port, bounded readiness, startup failure и owned cleanup |
| 09 | `feat(fixtures): manage owned users and cleanup` | UUID data, scopes, cleanup после ошибки, чужие записи не затрагиваются |
| 10 | `feat(baseline): classify exact known-defect signatures` | Узкая классификация; fixed/changed/network failures блокируют запуск |
| 11 | `feat(reports): emit complete and safe execution reports` | PASS/FAIL/XFAIL, HTML/JUnit/JSON, teardown precedence, escaping и manifest completeness |
| 12 | `test(crud): cover user lifecycle and conflicts` | Create/read/list/delete, duplicates и unknown users |
| 13 | `test(update): verify persistence and email changes` | Read-after-write, rename, conflict; проверки состояния вне known-defect gate |
| 14 | `test(validation): cover POST schema boundaries` | Полная исходная параметризация POST, null/missing/types/bounds/empty name |
| 15 | `test(validation): cover PUT schema boundaries` | Полная исходная параметризация PUT и состояние после отклонения |
| 16 | `test(protocol): cover raw bodies and encoded paths` | Non-object JSON, media type, percent/plus email paths |
| 17 | `test(auth): verify DELETE authorization behavior` | Missing/empty/incorrect/valid token и dev-only defect |
| 18 | `test(isolation): verify dev and prod independence` | Одинаковый email, разные данные; update/delete в dev не меняют prod |
| 19 | `feat(trace): add redacted HTTP and contract diagnostics` | Независимые флаги, request IDs, stdout/stderr/error redaction |
| 20 | `feat(generation): derive examples boundaries and check catalogue` | Пять операций, текущий schema subset, 13-category matrix; PASS/FAIL witnesses каждого detector |
| 21 | `feat(generation): add seeded fuzzing shrinking and replay` | jqwik, воспроизводимость, минимизация и replay сохранённого запроса |
| 22 | `feat(generation): provision verified resources for writes` | Reset/create/read seed, valid-path PUT/DELETE, negative inputs сохраняются, cleanup после ошибок |
| 23 | `feat(generation): emit bounded exploration evidence` | Examples/coverage/fuzzing, лимиты, phase counters, JUnit/NDJSON, различение findings и engine errors |
| 24 | `feat(security): scrub and verify retained evidence` | Atomic NDJSON, UTF-8, symlinks, injected filesystem failures, проверка остальных форматов |
| 25 | `feat(verification): run all scopes and enforce completeness` | Одна полная команда; отсутствие тестов/skip/duplicate/retry/abort не даёт ложный PASS |
| 26 | `ci(api): run isolated scopes and validate failure paths` | Матрица, independent failures, artifact guard, cleanup; реальные контрольные сбои CI |
| 27 | `docs: publish Java guide manual assets and verified reports` | Quick start, architecture, BUGS/SECURITY/strategy, Postman/cURL, samples с provenance, индекс SHA |
| 28 | `test(parity): verify the completed Java migration` | Сравнение исходов по каждому ID, чистая установка/clone, полный CI и итоговые evidence |

После последнего проверенного коммита — tag и GitHub Release v1.0.0 с точным SHA и ссылками на CI. Релиз публикуется в рамках отдельно разрешённой реализации, не при подготовке этого плана.

## Правила истории и проверок

- Отдельная Git history; существующие Python и TypeScript репозитории сохраняются.
- Коммиты создаются по мере реализации, после проверок; история не нарезается задним числом и не squash-ится.
- Subject сообщает реализованную функцию; body описывает назначение и выполненные проверки. Это смысловое описание, не GPG/SSH-подпись.
- Каждый этап проверяется в чистом checkout, без старых `target/`, reports и jqwik replay database. Зависимости можно брать из проверенного Maven cache; финальная установка дополнительно проверяется с отдельным Maven repository.
- После этапа запускается весь уже реализованный набор. До появления API-тестов — compile/static/unit/runner; затем добавляются реализованные dev/prod/isolation/generated части. Финально — полный набор.
- В baseline/parity режиме один worker и отсутствие retries. Ожидаемые IDs сравниваются с collected и executed IDs. Неожиданный skip, пустая collection или аварийно завершённый worker блокируют результат.
- Maven-проверки выполняются до `verify`, чтобы отложенная оценка integration failures не пропускала teardown. Самостоятельный координатор полного запуска дополнительно собирает исходы всех API scopes и возвращает общий ненулевой exit code при любом блокирующем сбое.
- Подробный индекс содержит фактический порядок, SHA, функцию, команды и проверенный результат. Будущий SHA собственного индексного коммита не выдумывается.

## Усиленные тесты инфраструктуры

Тестировать поведение собственного повторно используемого кода и возможность ложного PASS, а не дублировать тело каждого API-теста.

| Область | Обязательные отрицательные контрольные случаи |
| --- | --- |
| Config | CLI/env priority, malformed URL, invalid environment, zero/negative/nonfinite timeout |
| HTTP | Реальная 302 без перехода; сервер без ответа; разрыв соединения; точные bytes/headers/path |
| Contract | Неверный status/media/schema, invalid JSON, nonempty 204, refs, разные email oracles, отсутствие мутации payload |
| Known defects | Другая сигнатура, исправленный endpoint, сетевое исключение, ошибка setup/cleanup |
| Generation | Валидные/невалидные примеры с независимыми ожиданиями; каждый checker способен обнаружить нарушение; неподдержанная схема отклоняется до запросов |
| Replay | Один seed/config/version воспроизводит последовательность; минимизированный запрос воспроизводит контролируемый сбой; replay не полагается на случайно оставшееся состояние |
| Resources | Reset/create/read failures, negative path/body не исправляются fixture, переименованные ключи, повторная очистка, продолжение разрешённой best-effort cleanup |
| Evidence | HTML/XML escaping, вложенные secret fields, raw/URL-encoded secrets, malformed NDJSON/UTF-8, symlinks, read/write/rename failure, сохранение оригинала при ошибке |
| Runner | Проверка exit code и отчётов реальным JVM subprocess, lifecycle failure после known defect, пустая/неполная suite |
| CI | После deterministic FAIL генератор работает; engine FAIL блокирует job; scrub FAIL запрещает upload; cleanup выполняется |

JaCoCo используется для поиска непроверенных ветвей критических компонентов. Единый произвольный процент и совпадение количества тестов не являются критерием качества. Полноценное mutation testing можно добавить отдельным расширением после функционального переноса; контролируемые нарушения oracle/runner входят в обязательный объём уже здесь.

## Генератор и границы идентичности

Сохраняются назначение всех проверок, применимость к схеме, фазы, ограничения и stateful resource preparation. В отчёте остаются операция, фаза, seed, версии, запрос, минимальный counterexample, выполненные checks и причина остановки.

Java не обязана получать те же случайные запросы и shrink paths, что fast-check или Schemathesis. Для jqwik сохранить seed/config/version replay, а также явный replay сериализованного минимального запроса с нужной подготовкой ресурса. Секреты при replay подставляются из окружения, а не восстанавливаются из очищенного отчёта.

Исчерпание предусмотренного failure budget — допустимая явно отмеченная остановка. Сетевая ошибка, отмена, потеря worker, неподдержанная схема или неудачная запись evidence — engine failure. API findings могут оставаться информационными согласно политике исходного проекта; остальные ошибки блокируют запуск. Сам факт exit 0 не заменяет проверку operation/phase counters.

OpenAPI 3.0 нельзя автоматически трактовать как произвольную версию JSON Schema. Адаптер явно поддерживает текущие конструкции и отклоняет новые неподдержанные ограничения. Режимы email проверяются независимо; совпадение конечного контрольного корпуса не является доказательством универсальной эквивалентности всех RFC-email.

## CI и воспроизводимость

- Quality job плюс отдельные dev/prod/isolation jobs, `fail-fast: false`.
- Новый контейнер на scope, один pinned image digest, localhost ports и bounded readiness.
- Generated exploration запускается после успешной readiness даже при deterministic failures.
- Producer-side redaction и независимый fail-closed gate перед upload; действует также для isolation.
- Все загружаемые форматы явно разрешены; новый raw-формат требует собственного правила очистки.
- SHA-pinned Actions, минимальные repository permissions, checkout без сохранения credentials, retention 14 дней.
- JDK, Wrapper, plugins и dependency versions закреплены. Транзитивные зависимости и контрольные суммы учитываются отдельно: `pom.xml` сам по себе не является аналогом npm lockfile.
- Безопасная обработка stdout и stderr дочерних процессов. SIGTERM/SIGINT/shutdown, startup failure и cleanup проверяются; для SIGKILL документируется восстановление по owned container ID.
- Реальные workflow dispatch probes deterministic/generator/scrub сохраняются со ссылками и фактическими step outcomes; статический тест YAML их не заменяет.

## Критерии приёмки

1. Чистый clone работает с JDK + Docker через Maven Wrapper; Python и Node.js не нужны.
2. Все исходные API IDs сопоставлены с Java ID и проверяемой функцией; сравнение исходов выполнено по каждому ID на одном digest.
3. Назначение всех инфраструктурных проверок TypeScript и усилений U01–U06/I01–I02 сохранено и подтверждено evidence.
4. Точные XFAIL не скрывают новые ошибки, repaired behavior и teardown failures. Strict mode продолжает показывать реальные дефекты приложения.
5. Все применимые generated checks имеют положительный и отрицательный witness; пять операций представлены в отчётах, остановки и ограничения объяснены.
6. Replay и shrinking подтверждены контролируемым контрпримером; stateful PUT/DELETE работают по существующему пользователю.
7. HTML самодостаточен, JUnit/JSON/NDJSON читаемы, configured secrets отсутствуют после проверки, повреждённый artifact блокирует upload.
8. Нормальный CI зелёный на точном release SHA; намеренные failure probes подтверждают продолжение, блокировки и cleanup.
9. Документация и samples относятся к проверенным Java-запускам и содержат provenance. Индекс коммитов позволяет восстановить каждый этап.
10. Новый отдельный репозиторий и релиз опубликованы после выполнения разрешённой реализации; исходные репозитории сохранены.

## Официальные источники для выбора стека

- [Java SE support roadmap: Java 25 LTS](https://www.oracle.com/java/technologies/java-se-support-roadmap.html).
- [JUnit User Guide](https://docs.junit.org/6.1.0/overview.html).
- [jqwik: properties, seed, shrinking и JUnit Platform](https://jqwik.net/docs/current/user-guide).
- [JDK HttpClient](https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpClient.html).
- [networknt JSON Schema validator: dialects и custom formats](https://github.com/networknt/json-schema-validator).
- [Maven Failsafe: lifecycle и verify](https://maven.apache.org/surefire/maven-failsafe-plugin/).

Это архитектурный выбор для предстоящей реализации. Совместимость конкретных закреплённых версий и результаты Java-тестов должны быть доказаны выполнением плана.
