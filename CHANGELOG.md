<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# OpenAPI Companion Changelog

## [Unreleased]

## [2026.1.3]

### Added

- Review/star CTA: after 10 distinct real unresolved `$ref` findings, a
  one-time notification asks whether to rate the plugin on Marketplace,
  with a permanent "Don't ask again" option. The "license required"
  message never counts towards this -- only a genuinely broken
  reference does. Standard mechanism used catalog-wide since 2026-08-24
 , rolled out to this plugin now.

## [2026.1.2]

### Fixed

- Ctrl+Click/Ctrl+B on a `$ref` value showed "No usages found" instead
  of navigating, in every real IDE session (even with a trial license
  active) -- confirmed via live logging that the bundled-handler
  suppressor and this plugin's own reference resolution both worked
  correctly in isolation, but the platform never fell back to the
  generic reference after suppressing the bundled handler. Fixed by
  registering a real `GotoDeclarationHandler` (the same extension
  point the bundled handler itself uses), sidestepping that broken
  hand-off entirely. Same root cause and fix as asyncapi-companion
  0.1.1, ported here once confirmed working there first.

## [2026.1.1]

### Fixed

- Removed an internal Marketplace product code that had been
  mistakenly documented in this changelog -- no user-facing change.

## [2026.1.0]

### Changed

- **Version scheme**: this plugin now versions as `YYYY.MINOR.PATCH`
  (JetBrains's own convention for Paid/Freemium plugins) instead of
  semver (`0.1.x`) -- required by the same hard Marketplace validation
  rule already hit once with `ansible-companion`: `<product-descriptor>`'s
  `release-version` must share its leading digits with the plugin's own
  version.

### Added

- `<product-descriptor>` in `plugin.xml`, with the real product code
  JetBrains Marketplace assigned on enrolling this plugin in the Paid
  pricing model. No `optional="true"` -- unlike every Freemium plugin
  in this catalog, this is 100% Paid, no free tier, so the base plugin
  itself requires a license.

## [0.1.0]

### Added

- Go-to-definition (Ctrl+Click) for `$ref` values in OpenAPI/Swagger
  specs, resolved entirely against local files -- no HTTP client
  anywhere in this plugin. Works for both JSON and YAML specs, same-file
  and cross-file references.
- Broken `$ref` values (pointing at a document/pointer that doesn't
  exist) are flagged with a warning.
- Specs detected by real content (`openapi: "3.x"` / `swagger: "2.0"`
  top-level key), never by file extension.
- 100% Paid, no free tier: every feature above requires a license.

### Known gaps (v1 scope cuts, tracked for a future release)

- Cross-format resolution (JSON `$ref` into a YAML file, or vice versa)
  isn't supported yet -- same-format only.
- No OAS-version-aware `$ref` convention validation yet.
- No full JSON Schema instance validation (OAS 3.1 type unions
  included) -- a meaningfully larger scope than reference resolution.

[Unreleased]: https://github.com/GapHunterLabs/openapi-companion/compare/2026.1.3...HEAD
[2026.1.3]: https://github.com/GapHunterLabs/openapi-companion/compare/2026.1.2...2026.1.3
[2026.1.2]: https://github.com/GapHunterLabs/openapi-companion/compare/2026.1.1...2026.1.2
[2026.1.1]: https://github.com/GapHunterLabs/openapi-companion/compare/2026.1.0...2026.1.1
[2026.1.0]: https://github.com/GapHunterLabs/openapi-companion/compare/0.1.0...2026.1.0
[0.1.0]: https://github.com/GapHunterLabs/openapi-companion/commits/0.1.0
