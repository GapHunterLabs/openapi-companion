# OpenAPI Companion

IntelliJ-family plugin. Reliable go-to-definition (Ctrl+Click / Ctrl+B)
for `$ref` values in OpenAPI/Swagger specs written in JSON or YAML,
resolved entirely against local files, plus a warning on any `$ref`
that doesn't actually resolve.

**100% Paid, no free tier.** Unlike every other plugin in this catalog,
this one has no permanently-free base -- every feature requires a
license after the standard JetBrains 30-day trial.

## Why it exists

Built from real, current evidence across four independent sources in
this exact space, not assumptions:

- **OpenAPI Editor** (Sebastian Monte, paid, 47,670 downloads): a
  multi-year, recurring pattern across real reviews (2021 through
  2025) of falling two or more JetBrains releases behind after every
  new IDE version -- "Not worth buying until its fixed," "the fix
  requires several weeks" -- plus a direct functional gap: *"Doesn't
  support external references; not usable."*
- **OpenAPI (Swagger) Editor** (42Crunch, freemium, ~700K downloads):
  *"please fix the high memory usage when the live preview is open...
  it keep on growing until the IDE crashes,"* plus incomplete OAS 3.1
  support despite advertising it.
- **Zalando OpenAPI Editor** (free, abandoned since 2023, ~722K
  downloads): *"Makes the whole IDE pretty much unusable on large
  openapi files. We have like 10+ seconds lags every couple seconds."*
- JetBrains's own bundled OpenAPI plugin -- independently documented
  elsewhere in this catalog (`api-security-companion`'s own evidence
  trail) as *"catastrophically slow... turned into torture"* even on
  small 10-15-endpoint files.

Four different vendors, four different pricing models, the same real
gap: nobody in this space reliably resolves external references without
freezing the IDE.

## Why built this way

- **No HTTP client anywhere in this plugin.** `$ref` resolution walks
  local files only (`VfsUtilCore.findRelativeFile`, the same confirmed
  pattern `json-schema-companion` already uses) -- an `http(s)://` ref
  simply shows as unresolved, never a network fetch. Direct fix for the
  "not usable" external-reference gap above.
- **Two independent PSI backends, not one lenient parser.** OpenAPI
  specs are written in JSON and YAML close to interchangeably in
  practice, so this plugin resolves `$ref` natively against both --
  `com.intellij.json.psi` and `org.jetbrains.yaml.psi` -- rather than
  favoring one format and leaving the other half-supported.
- **Detected by real content, never by file extension** (`openapi:
  "3.x"` / `swagger: "2.0"` at the top level) -- a `.json`/`.yaml` file
  alone means nothing, same principle already established elsewhere in
  this catalog.
- Every rule here is a normal `Annotator`/`PsiReferenceContributor`,
  which the platform's own background daemon already schedules off the
  UI thread -- the direct fix for the memory-leak/10-second-lag/
  "torture" complaints above, all of which are IDE-freezing bugs.

## Usage

Open a JSON or YAML file with a top-level `openapi: "3.x"` or `swagger:
"2.0"` field. Ctrl+Click (or Ctrl+B) any `$ref` value -- same-file
pointers and cross-file references both navigate to the real
definition. A `$ref` that doesn't resolve is flagged with a warning.

## v1 scope cuts (documented, not silent)

- Cross-format resolution (a JSON file's `$ref` pointing into a YAML
  file, or vice versa) isn't supported yet -- same-format only. Most
  real-world multi-file specs stay consistent within one format, but
  this is a real gap, not a forgotten one.
- No OAS-version-aware validation yet (e.g. flagging a Swagger 2.0
  `$ref` convention used inside an OpenAPI 3.x document, or vice
  versa) -- tracked for a future release.
- No full JSON Schema instance validation (checking `example`/`default`
  values against their declared `type`, including OAS 3.1's type-union
  array syntax) -- a meaningfully larger scope than reference
  resolution, deliberately not attempted in v1.

## Support

Questions, bug reports, or team/volume licensing: contact us at
**gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
