package dev.gaphunter.openapicompanion.example

import java.math.BigDecimal

/** Stand-in for a YAML value whose type is ambiguous. */
object UnknownValue

/**
 * Builds a [SpecNode] tree from plain Kotlin maps/lists/scalars, with each
 * node's anchor set to its own pointer (`#/components/schemas/Pet/example`)
 * and each keyed node's keyAnchor to that pointer plus `:key` -- so a test
 * can assert exactly WHERE a finding lands, with no PSI involved.
 */
fun node(value: Any?, path: String = "#", key: String? = null): SpecNode<String> = when (value) {
    null -> SpecNode.Null(path, key)
    UnknownValue -> SpecNode.Unknown("?", path, key)
    is Boolean -> SpecNode.Bool(value, path, key)
    is Int -> SpecNode.Num(BigDecimal(value), path, key)
    is Long -> SpecNode.Num(BigDecimal(value), path, key)
    is Double -> SpecNode.Num(BigDecimal(value.toString()), path, key)
    is String -> SpecNode.Str(value, path, key)
    is List<*> -> SpecNode.Arr(value.mapIndexed { i, item -> node(item, "$path/$i") }, path, key)
    is Map<*, *> -> SpecNode.Obj(
        value.entries.associate { (k, v) -> k as String to node(v, "$path/$k", "$path/$k:key") },
        path,
        key,
    )
    else -> error("unsupported test value: $value")
}
