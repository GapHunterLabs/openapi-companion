package dev.gaphunter.openapicompanion.usage

import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.gaphunter.openapicompanion.detection.OpenApiDetector.SpecVersion
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

private const val REF_KEYWORD = "\$ref"

/**
 * Lists the reusable components a spec file declares -- the only place the
 * unused-component check touches spec PSI; everything after this works on
 * plain [ComponentDeclaration]s.
 *
 * OAS 3.x sections are exactly the component types Spectral's
 * `oasUnusedComponent` checks. `securitySchemes` is deliberately absent:
 * they're used by NAME in `security:` requirements, never through `$ref`,
 * so flagging them would be a guaranteed false positive. OAS 3.1
 * `pathItems` isn't checked upstream either. Swagger 2.0 covers
 * `definitions` only, the scope of Spectral's `oas2-unused-definition`.
 */
object ComponentCollector {
    private val OAS3_SECTIONS = listOf(
        "schemas", "responses", "parameters", "examples", "requestBodies", "headers", "links", "callbacks",
    ).map { listOf("components", it) }
    private val SWAGGER2_SECTIONS = listOf(listOf("definitions"))
    private val SCHEMA_SECTION_KEYS = setOf("schemas", "definitions")

    fun collect(file: PsiFile, version: SpecVersion): List<ComponentDeclaration<PsiElement>> {
        val sections = if (version == SpecVersion.OPENAPI_3) OAS3_SECTIONS else SWAGGER2_SECTIONS
        return when (file) {
            is YAMLFile -> collectYaml(file, sections)
            is JsonFile -> collectJson(file, sections)
            else -> emptyList()
        }
    }

    private fun collectYaml(file: YAMLFile, sections: List<List<String>>): List<ComponentDeclaration<PsiElement>> {
        val root = file.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return emptyList()
        return sections.flatMap { path ->
            val section = path.fold<String, YAMLMapping?>(root) { mapping, key ->
                mapping?.getKeyValueByKey(key)?.value as? YAMLMapping
            } ?: return@flatMap emptyList()
            val isSchemaSection = path.last() in SCHEMA_SECTION_KEYS
            section.keyValues.map { keyValue ->
                ProgressManager.checkCanceled()
                val body = if (isSchemaSection) keyValue.value as? YAMLMapping else null
                val discriminator = body?.getKeyValueByKey("discriminator")
                ComponentDeclaration(
                    section = path.joinToString("/"),
                    name = keyValue.keyText,
                    anchor = keyValue.key ?: keyValue,
                    hasDiscriminator = discriminator != null,
                    discriminatorMappingValues = ((discriminator?.value as? YAMLMapping)?.getKeyValueByKey("mapping")?.value as? YAMLMapping)
                        ?.keyValues?.mapNotNull { (it.value as? YAMLScalar)?.textValue }.orEmpty(),
                    allOfRefs = (body?.getKeyValueByKey("allOf")?.value as? YAMLSequence)
                        ?.items?.mapNotNull { ((it.value as? YAMLMapping)?.getKeyValueByKey(REF_KEYWORD)?.value as? YAMLScalar)?.textValue }
                        .orEmpty(),
                )
            }
        }
    }

    private fun collectJson(file: JsonFile, sections: List<List<String>>): List<ComponentDeclaration<PsiElement>> {
        val root = file.topLevelValue as? JsonObject ?: return emptyList()
        return sections.flatMap { path ->
            val section = path.fold<String, JsonObject?>(root) { obj, key ->
                obj?.findProperty(key)?.value as? JsonObject
            } ?: return@flatMap emptyList()
            val isSchemaSection = path.last() in SCHEMA_SECTION_KEYS
            section.propertyList.map { property ->
                ProgressManager.checkCanceled()
                val body = if (isSchemaSection) property.value as? JsonObject else null
                val discriminator = body?.findProperty("discriminator")
                ComponentDeclaration(
                    section = path.joinToString("/"),
                    name = property.name,
                    anchor = property.nameElement,
                    hasDiscriminator = discriminator != null,
                    discriminatorMappingValues = ((discriminator?.value as? JsonObject)?.findProperty("mapping")?.value as? JsonObject)
                        ?.propertyList?.mapNotNull { (it.value as? JsonStringLiteral)?.value }.orEmpty(),
                    allOfRefs = (body?.findProperty("allOf")?.value as? JsonArray)
                        ?.valueList?.mapNotNull { ((it as? JsonObject)?.findProperty(REF_KEYWORD)?.value as? JsonStringLiteral)?.value }
                        .orEmpty(),
                )
            }
        }
    }
}
