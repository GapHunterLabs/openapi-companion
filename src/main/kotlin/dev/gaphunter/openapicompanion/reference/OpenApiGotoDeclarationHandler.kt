package dev.gaphunter.openapicompanion.reference

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.gaphunter.openapicompanion.licensing.CheckLicense
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Registered `GotoDeclarationHandler` for `$ref` values in OpenAPI/
 * Swagger documents (JSON and YAML) -- the direct fix for a real,
 * confirmed platform bug (2026-08-13, see the
 * `openapi_companion_ctrlclick_broken_with_trial` memory entry for the
 * full logged investigation, done against asyncapi-companion first and
 * confirmed fixed there before porting here): [OpenApiJsonSchemaGtdSuppressor]
 * correctly suppresses the bundled `JsonSchemaGotoDeclarationHandler`
 * on every real Ctrl+B attempt, and [YamlOpenApiRefReference]/
 * [JsonOpenApiRefReference] correctly resolve `$ref` values -- but in a
 * real `runIde` sandbox, the platform never falls back to the generic
 * `PsiReference` resolution after suppressing the bundled handler, so
 * Ctrl+B shows "No usages found" despite everything else working
 * correctly.
 *
 * Registering our own `GotoDeclarationHandler` sidesteps that broken
 * hand-off entirely: `GotoDeclarationHandler`s are consulted directly
 * by the platform, the same extension point the bundled handler itself
 * uses -- no suppress-then-fallback coordination required. Reuses the
 * exact same resolution logic as the `PsiReference` classes (calling
 * `.resolve()` on them, which already gates on [dev.gaphunter.openapicompanion.licensing.CheckLicense]
 * -- an unlicensed user gets no targets here either, same as before)
 * so there is only one place that actually knows how to resolve a
 * `$ref`.
 *
 * Walks up to the enclosing `YAMLKeyValue`/`JsonProperty` first, then
 * reads `.value` explicitly, rather than assuming `sourceElement`'s
 * parent is already the value scalar/literal -- the caret can land on
 * either the `$ref` KEY token or the value string, and those are
 * siblings under the same key-value pair, not ancestor/descendant (a
 * caret on the key never has the value scalar among its PSI
 * ancestors). Confirmed as the real root cause via live logging on
 * asyncapi-companion before writing this version directly this way.
 */
class OpenApiGotoDeclarationHandler(
    private val isLicensed: () -> Boolean = { CheckLicense.isLicensed() == true },
) : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        if (sourceElement == null) return null

        val yamlKeyValue = PsiTreeUtil.getParentOfType(sourceElement, YAMLKeyValue::class.java, false)
        if (yamlKeyValue != null) {
            val valueScalar = yamlKeyValue.value as? YAMLScalar
            if (valueScalar != null) {
                YamlOpenApiRefUtil.asRefKeyValue(valueScalar) ?: return null
                val target = YamlOpenApiRefReference(valueScalar, isLicensed).resolve() ?: return null
                return arrayOf(target)
            }
        }

        val jsonProperty = PsiTreeUtil.getParentOfType(sourceElement, JsonProperty::class.java, false)
        if (jsonProperty != null) {
            val valueLiteral = jsonProperty.value as? JsonStringLiteral
            if (valueLiteral != null) {
                JsonOpenApiRefUtil.asRefProperty(valueLiteral) ?: return null
                val target = JsonOpenApiRefReference(valueLiteral, isLicensed).resolve() ?: return null
                return arrayOf(target)
            }
        }

        return null
    }
}
