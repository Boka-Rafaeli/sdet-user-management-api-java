# Reports

`sample/` contains verified Java output, not copied TypeScript results. Its `provenance.json`
identifies source commit, command, runtime and pinned image. Each deterministic HTML is a
self-contained file that can be opened without a server. Generated NDJSON uses schemaVersion 1.

`run/` is ignored and created by local verification. Reports are sanitized at production and
independently checked before CI upload. Generated findings are informational; an engine error
or malformed artifact is blocking. Samples do not prove future runs or arbitrary application images.
