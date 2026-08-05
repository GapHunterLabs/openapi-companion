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

**Still not 100% confirmed -- and live verification is now blocked by a
separate, more fundamental problem (2026-08-05).** Attempted the live
Ctrl+B check with the usual temporary `CheckLicense.isLicensed()`
bypass, exactly as done for the original screenshot session. This time
it didn't work the same way: on launch, the IDE now shows its own
**platform-level** "Manage Subscriptions" dialog for OpenAPI Companion
("Activate to enable"), and closing it immediately triggers a second
"Confirm Restart -- Application restart is necessary to disable
features requiring license" dialog. Clicking Restart doesn't actually
restart anything inside a `./gradlew runIde` sandbox (there's no
external launcher process to relaunch it, unlike a real installed IDE)
-- it just kills the whole process (`finished with non-zero exit value
7`). Relaunching hits the exact same two dialogs again, in a loop with
no way to dismiss them and just use the IDE normally.

**Root cause (inferred, not deeply investigated further --
CLAUDE.md's anti-loop rule applies to this too):** this is new
behavior that only appeared after adding a *real*, non-`optional`
`<product-descriptor>` for the Paid pricing model enrollment (see
[[openapi_companion_v010]]). `ansible-companion`/`api-security-companion`
never show this, because their descriptors both have
`optional="true"` (Freemium -- the platform doesn't gate a Freemium
plugin's own launch on licensing status). For a 100%-Paid descriptor,
the platform apparently now enforces its *own* licensing UI
independently of -- and prior to -- anything this plugin's own
`CheckLicense.kt` does. The temporary code-level bypass this catalog
has used successfully for every other demo/screenshot session
(including this exact plugin's *first* screenshot session, back when
it had no product-descriptor at all) doesn't reach this: it's a
platform-level gate, not something reachable from plugin code.

**Consequence:** as of now, this plugin **cannot be interactively
tested via `./gradlew runIde` at all** without a real, JetBrains-signed
trial or license -- not just Ctrl+B, everything, since the dialog loop
blocks normal use of the sandbox entirely. This is a bigger, more
urgent finding than the original Ctrl+B mystery: it affects all future
local testing/demo work on this plugin, not just this one check.

**"Start trial" tried, 2026-08-05 (same session, after re-launching
clean, no code bypass): also blocked, but for a clear, external
reason.** Clicking "Start Trial" returns "Trial license is not
supported for your product version" -- makes sense once connected to
[[api_security_companion_pro_v020]]'s own note: the **Trial Period**
field on a plugin's Marketplace Sales page only becomes settable once
that plugin clears moderation and reaches "In stock" status.
`openapi-companion`'s `2026.1.0` build is still "Submitted", not yet
approved, so no trial period has ever been configured for it -- there
is nothing for "Start Trial" to grant yet. This isn't a bug to fix;
it's the same external approval dependency already tracked for the
Trial Period step itself.

**Ctrl+B specifically is still unconfirmed, blocked by an external
dependency, not a code problem.** Both realistic verification paths
are now exhausted for this session:
1. Code-level `CheckLicense` bypass -> blocked by the platform's own
   subscription/restart dialog loop (see above).
2. Real "Start Trial" -> blocked by the plugin not yet being approved/
   "In stock" on Marketplace, so no trial period exists to grant.

**Next real step, once JetBrains approves the `2026.1.0` submission:**
set the Trial Period on the Sales page (30 days, matching the
README/outreach promise, same step already pending for
`api-security-companion`), *then* retry "Start Trial" in a fresh
`runIde` sandbox -- that should finally grant a real, platform-recognized
trial and unblock live testing of this fix (and of the plugin in
general) with no code changes needed on either side.

The suppressor fix itself (code, tests, `verifyPlugin`) remains solid
regardless of this blocker, and being licensed-gated means it's
inert/safe if wrong -- keep it.

**Impact:** cosmetic/UX only regardless of outcome here -- the
underlying `resolve()` logic that the warning annotator depends on
works correctly (proven independently), so broken-ref detection was
never affected by any of this.
