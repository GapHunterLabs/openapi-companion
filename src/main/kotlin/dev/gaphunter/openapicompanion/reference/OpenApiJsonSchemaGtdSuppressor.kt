package dev.gaphunter.openapicompanion.reference

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.JsonSchemaGotoDeclarationSuppressor
import dev.gaphunter.openapicompanion.detection.OpenApiDetector
import dev.gaphunter.openapicompanion.licensing.CheckLicense

/**
 * Suppresses the platform's own bundled JSON Schema go-to-declaration
 * (`com.jetbrains.jsonSchema.impl.JsonSchemaGotoDeclarationHandler`,
 * part of `com.intellij.modules.json`) on recognized OpenAPI/Swagger
 * files, so this plugin's own [JsonOpenApiRefReference]/
 * [YamlOpenApiRefReference] get a chance to resolve instead --
 * `GotoDeclarationHandler`s are consulted by the platform before
 * generic `PsiReference`-based resolution, so without this the bundled
 * handler wins first regardless of what this plugin contributes (see
 * `KNOWN_ISSUES.md` Round 1).
 *
 * Only suppresses when [isLicensed] is true: an unlicensed user would
 * otherwise lose the bundled navigation entirely and get nothing in
 * its place, since this plugin's own references intentionally never
 * resolve without a license (100% Paid, no free tier). Licensed users
 * get this plugin's more accurate resolution instead of the bundled
 * one; unlicensed users keep whatever the bundled handler already gave
 * them, unchanged.
 */
class OpenApiJsonSchemaGtdSuppressor(
    private val isLicensed: () -> Boolean = { CheckLicense.isLicensed() == true },
) : JsonSchemaGotoDeclarationSuppressor {
    override fun shouldSuppressGtd(element: PsiElement): Boolean {
        if (!isLicensed()) return false
        val file = element.containingFile ?: return false
        return OpenApiDetector.detect(file.text) != null
    }
}
