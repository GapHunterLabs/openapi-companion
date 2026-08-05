package dev.gaphunter.openapicompanion.pointer

import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLValue

/**
 * RFC 6901 JSON Pointer resolution against real YAML PSI -- the YAML
 * counterpart of [JsonPointer], needed because OpenAPI/Swagger specs
 * are written in YAML at least as often as JSON in practice, and a
 * `$ref` still uses the same RFC 6901 pointer syntax regardless of
 * which format the document is serialized in.
 * [org.jetbrains.yaml.psi.YAMLMapping.getKeyValueByKey] does the exact
 * per-segment lookup [JsonObject.findProperty] does for JSON -- no need
 * to hand-roll key matching.
 */
object YamlPointer {
    fun resolve(root: YAMLValue, pointer: String): PsiElement? {
        if (pointer.isEmpty()) return root
        if (!pointer.startsWith("/")) return null

        var current: YAMLValue = root
        val segments = pointer.substring(1).split("/")
        for ((index, rawSegment) in segments.withIndex()) {
            val segment = unescape(rawSegment)
            val isLast = index == segments.lastIndex
            when (val node = current) {
                is YAMLMapping -> {
                    val keyValue = node.getKeyValueByKey(segment) ?: return null
                    if (isLast) return keyValue.key ?: keyValue
                    current = keyValue.value ?: return null
                }
                is YAMLSequence -> {
                    val itemIndex = segment.toIntOrNull() ?: return null
                    val items = node.items
                    if (itemIndex !in items.indices) return null
                    val value = items[itemIndex].value ?: return null
                    if (isLast) return value
                    current = value
                }
                else -> return null
            }
        }
        return current
    }

    /** Per RFC 6901: `~1` -> `/` must be applied before `~0` -> `~`. */
    private fun unescape(segment: String): String = segment.replace("~1", "/").replace("~0", "~")
}
