package dev.gaphunter.openapicompanion.reference

import dev.gaphunter.openapicompanion.detection.OpenApiDetector
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

private const val REF_KEYWORD = "\$ref"

/** YAML counterpart of [JsonOpenApiRefUtil] -- shared "is this scalar a
 * `$ref` VALUE inside a recognized OpenAPI/Swagger YAML file" check. */
object YamlOpenApiRefUtil {
    fun asRefKeyValue(scalar: YAMLScalar): YAMLKeyValue? {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return null
        if (keyValue.keyText != REF_KEYWORD) return null
        if (keyValue.value !== scalar) return null
        if (OpenApiDetector.detect(scalar.containingFile.text) == null) return null
        return keyValue
    }
}
