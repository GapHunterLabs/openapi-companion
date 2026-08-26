package dev.gaphunter.openapicompanion.highlighting

import com.intellij.json.psi.JsonStringLiteral
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import dev.gaphunter.openapicompanion.licensing.CheckLicense
import dev.gaphunter.openapicompanion.reference.JsonOpenApiRefReference
import dev.gaphunter.openapicompanion.reference.JsonOpenApiRefUtil
import dev.gaphunter.openapicompanion.reference.YamlOpenApiRefReference
import dev.gaphunter.openapicompanion.reference.YamlOpenApiRefUtil
import dev.gaphunter.openapicompanion.review.ReviewPrompt
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Flags a `$ref` value that fails to resolve, in both JSON- and
 * YAML-formatted OpenAPI/Swagger specs -- real, visible feedback for a
 * broken reference, on top of the go-to-definition navigation the two
 * reference contributors already provide for valid ones. Handles both
 * languages in one class (registered twice in plugin.xml, once per
 * language) rather than duplicating this dispatch logic.
 *
 * Unlicensed: shows a distinct "license required" message instead of a
 * generic "cannot resolve" one -- both reference classes' `resolve()`
 * already return null unconditionally when unlicensed (100% Paid, no
 * free tier), so without this special case every single `$ref` would
 * misleadingly look broken instead of explaining why.
 */
class OpenApiRefAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val refText: String
        val resolves: Boolean
        when (element) {
            is JsonStringLiteral -> {
                JsonOpenApiRefUtil.asRefProperty(element) ?: return
                refText = element.value
                resolves = JsonOpenApiRefReference(element).resolve() != null
            }
            is YAMLScalar -> {
                YamlOpenApiRefUtil.asRefKeyValue(element) ?: return
                refText = element.textValue
                resolves = YamlOpenApiRefReference(element).resolve() != null
            }
            else -> return
        }

        if (CheckLicense.isLicensed() != true) {
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "OpenAPI Companion: license required to resolve \$ref values")
                .range(element.textRange)
                .create()
            return
        }

        if (!resolves) {
            holder.newAnnotation(HighlightSeverity.WARNING, "Cannot resolve reference '$refText'")
                .range(element.textRange)
                .create()
            // Only the real "broken reference" finding counts towards the CTA --
            // the "license required" branch above is a monetization gate, not a
            // problem in the user's spec, and must never inflate the counter.
            val file = element.containingFile
            val lineNumber = file.viewProvider.document?.getLineNumber(element.textRange.startOffset)?.plus(1) ?: 0
            ReviewPrompt.recordHit(file.project, "${file.virtualFile?.path}:$lineNumber:$refText")
        }
    }
}
