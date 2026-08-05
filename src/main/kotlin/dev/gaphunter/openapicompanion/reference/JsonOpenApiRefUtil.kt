package dev.gaphunter.openapicompanion.reference

import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import dev.gaphunter.openapicompanion.detection.OpenApiDetector

private const val REF_KEYWORD = "\$ref"

/** Shared "is this string literal a `$ref` VALUE inside a recognized
 * OpenAPI/Swagger JSON file" check -- used by both
 * [JsonOpenApiRefReferenceContributor] (to decide whether to offer
 * navigation) and [dev.gaphunter.openapicompanion.highlighting.OpenApiRefAnnotator]
 * (to decide whether to check resolution), so the two never drift out
 * of sync. */
object JsonOpenApiRefUtil {
    fun asRefProperty(literal: JsonStringLiteral): JsonProperty? {
        if (literal.isPropertyName) return null
        val property = literal.parent as? JsonProperty ?: return null
        if (property.name != REF_KEYWORD) return null
        if (property.value !== literal) return null
        if (OpenApiDetector.detect(literal.containingFile.text) == null) return null
        return property
    }
}
