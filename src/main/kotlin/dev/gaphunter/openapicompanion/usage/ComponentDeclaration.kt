package dev.gaphunter.openapicompanion.usage

/**
 * One reusable component declared in a spec file, reduced to plain data by
 * [ComponentCollector]. [anchor] is generic so the pure rules in
 * [ComponentUsageMatcher] can be tested with plain values instead of live PSI.
 *
 * The last three fields only carry data for schemas -- they exist for
 * discriminator semantics and stay empty for every other section.
 */
data class ComponentDeclaration<out A>(
    val section: String,
    val name: String,
    val anchor: A,
    val hasDiscriminator: Boolean = false,
    val discriminatorMappingValues: List<String> = emptyList(),
    val allOfRefs: List<String> = emptyList(),
)
