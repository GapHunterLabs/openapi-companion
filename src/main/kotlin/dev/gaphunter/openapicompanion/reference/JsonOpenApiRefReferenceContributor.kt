package dev.gaphunter.openapicompanion.reference

import com.intellij.json.psi.JsonStringLiteral
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

/**
 * Wires [JsonOpenApiRefReference] up as a real, navigable go-to-definition
 * (Ctrl+Click / Ctrl+B) on `$ref` string values inside JSON files
 * recognized as OpenAPI/Swagger specs by [dev.gaphunter.openapicompanion.detection.OpenApiDetector].
 */
class JsonOpenApiRefReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(JsonStringLiteral::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val literal = element as? JsonStringLiteral ?: return PsiReference.EMPTY_ARRAY
                    JsonOpenApiRefUtil.asRefProperty(literal) ?: return PsiReference.EMPTY_ARRAY
                    return arrayOf(JsonOpenApiRefReference(literal))
                }
            },
        )
    }
}
