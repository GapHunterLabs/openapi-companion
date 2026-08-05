# Known issues log — OpenAPI Companion

Real bugs found during development/verification, with root cause and fix
(or, when still open, what was ruled out and what's left to check). Not a
TODO list.

## Round 1 (2026-08-04) — Ctrl+Click/Ctrl+B navigation doesn't jump in a real `runIde` sandbox, even though `resolve()` is proven correct

**Symptom:** in a real IntelliJ IDEA Ultimate sandbox, placing the caret
inside a valid `$ref` value (e.g. `#/components/schemas/Order`) and
pressing Ctrl+B (or Ctrl+Click) shows "No usages found in Project Files"
instead of navigating to the schema. The plugin's own warning annotation
on a broken `$ref` (`OpenApiRefAnnotator`) works correctly in the same
session — only PSI-reference-based navigation is affected.

**Ruled out, with evidence, not guesses:**
- **Not a code bug in `resolve()`/the pointer logic.** Two tests added
  to `YamlOpenApiRefReferenceTest` prove this: one goes through the real
  `PsiReferenceContributor` extension pipeline (`getReferenceAtCaretPosition`,
  the same path Ctrl+B uses) and confirms a `YamlOpenApiRefReference` is
  correctly attached at the caret; a second uses the exact byte-for-byte
  content of `demo/openapi.yaml` (deep nesting, a cross-file `$ref`, a
  broken `$ref` sibling) and confirms it resolves to the right element.
  Both pass.
- **Not the plugin failing to load.** Confirmed via Settings → Plugins →
  Installed in the live sandbox: "OpenAPI Companion" 0.1.0, enabled.
- **Not JetBrains's bundled "OpenAPI Specifications" plugin
  (`com.intellij.swagger`, bundled in IDEA Ultimate).** That plugin
  registers its own `psi.referenceContributor` for `$ref` in YAML/JSON
  (`SwYamlReferenceContributor`/`SwJsonReferenceContributor`, confirmed
  by extracting its `plugin.xml` from `swagger.jar`) plus two
  `json.jsonSchemaGotoDeclarationSuppressor` entries — a real, plausible
  source of interference. It was disabled in Settings → Plugins,
  confirmed via `disabled_plugins.txt` in the sandbox config, and the
  IDE restarted. Same "No usages found" result regardless.

**Still open — not yet root-caused.** Because `BasePlatformTestCase`
doesn't load the real Ultimate-bundled plugin set the same way a real
`runIde` sandbox does, and because IntelliJ's generic JSON Schema
framework (`com.intellij.modules.json`, independent of the Swagger
plugin) can auto-associate `.yaml` files matching known API-spec
patterns with a schema and contribute its own `$ref` navigation, that
generic (non-Swagger) mechanism is the next thing to check -- not yet
confirmed. Whatever the actual cause, "No usages found" (not "Cannot
find declaration to go to") specifically indicates *no* reference is
being returned at that caret in the live session, not a resolution
failure -- so this is about reference-contribution precedence/dispatch
among multiple contributors on the same PSI element, not about
[[CheckLicense]] or our pointer-resolution logic.

**Impact:** cosmetic/UX only for now -- the underlying `resolve()` logic
that the warning annotator depends on works correctly (proven above and
via the annotator itself working live), so broken-ref detection is not
affected. Go-to-definition via Ctrl+B/Ctrl+Click may not work for some
users in IDEA Ultimate until this is root-caused. Not blocking the v1
release; revisit before advertising "go-to-definition" as unconditionally
reliable in the Marketplace listing if this turns out to affect the
Community-only (non-Swagger-bundled) case too.
