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

**Likely root cause found 2026-08-05, fix implemented, not yet
confirmed live (no sandbox available this session — user was away,
this was done through code/log verification only, same discipline as
always: don't guess, but also don't block on a live check that isn't
available).** Read `com.intellij.modules.json`'s own bundled
`plugin.xml` directly (extracted from `intellij.json.backend.jar`,
same technique used to find the Swagger plugin's registrations) and
found: `<gotoDeclarationHandler id="JsonSchemaGotoDeclarationHandler"
implementation="com.jetbrains.jsonSchema.impl.JsonSchemaGotoDeclarationHandler" />`.
This is a **different, higher-priority extension point** than
`psi.referenceContributor` — the platform consults registered
`GotoDeclarationHandler`s *before* falling back to generic
`PsiReference` resolution for Ctrl+B/Ctrl+Click. Since
`com.intellij.modules.json` is one of this plugin's own hard
dependencies, this handler is *always* present regardless of the
Swagger plugin's enabled state — explains why disabling Swagger didn't
change anything. This also lines up exactly with the earlier
diagnostic: "No usages found" (not "Cannot find declaration") is what
the platform shows when no `GotoDeclarationHandler` returns a target
*and* the element isn't classified as a reference either — consistent
with `JsonSchemaGotoDeclarationHandler` claiming the element first,
attempting its own generic-JSON-Schema-shaped resolution (which likely
doesn't handle OAS's specific `#/components/schemas/X` convention the
same way this plugin's own `YamlPointer`/`JsonPointer` do), and coming
up empty.

**Fix:** `com.intellij.modules.json` also declares an official
suppression mechanism for exactly this situation —
`<extensionPoint qualifiedName="com.intellij.json.jsonSchemaGotoDeclarationSuppressor"
interface="com.jetbrains.jsonSchema.extension.JsonSchemaGotoDeclarationSuppressor" />`,
a one-method interface (`shouldSuppressGtd(PsiElement): Boolean`,
confirmed via `javap` against the actual bundled class since this
isn't part of the public SDK docs). The Swagger plugin already uses
this same mechanism for its own recognized files (`SwJsonSchemaGtdSuppressor`),
which is what first suggested this was a real, intended extension
point rather than something to work around unofficially. Implemented
`OpenApiJsonSchemaGtdSuppressor`, registered under
`json.jsonSchemaGotoDeclarationSuppressor` in `plugin.xml`: suppresses
the bundled handler only on files `OpenApiDetector` recognizes **and**
only when [[CheckLicense]] reports a valid license — an unlicensed
user must never lose the bundled navigation to gain nothing in its
place, since this plugin's own references intentionally never resolve
without a license either (100% Paid, no free tier). 3 new tests in
`OpenApiJsonSchemaGtdSuppressorTest`, all passing; full suite 21/21;
`verifyPlugin` 6/6 Compatible, no internal/experimental/override-only
API flags raised against this interface (the build's own hard-fail
gates on those categories didn't trigger, which is itself a signal
this is a stable, intentionally-public extension point).

**Still not 100% confirmed:** this was verified through tests and log
analysis, never against a live `runIde` sandbox with an actual Ctrl+B
press (no sandbox was available when this fix was written). The
mechanism, the interface, and the registration are all confirmed real
and correctly wired — what's unconfirmed is whether
`JsonSchemaGotoDeclarationHandler` is in fact the exact handler
`GotoDeclarationAction` was hitting (vs. some other handler in the
dispatch chain). **Next step: launch `runIde`, open `demo/openapi.yaml`
with a valid license simulated (temporary bypass, same one-line-revert
pattern as the original screenshot session), and press Ctrl+B on the
`Order` `$ref` for real.** If this doesn't fix it, the suppressor
being licensed-gated means it's a safe, inert addition either way —
worth keeping regardless of outcome, since it's philosophically the
right thing to register given this plugin depends on
`com.intellij.modules.json`.

**Impact:** cosmetic/UX only regardless of outcome here -- the
underlying `resolve()` logic that the warning annotator depends on
works correctly (proven independently), so broken-ref detection was
never affected by any of this.
