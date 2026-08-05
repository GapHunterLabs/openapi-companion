<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# OpenAPI Companion Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/GapHunterLabs/openapi-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/openapi-companion/commits/0.1.0
