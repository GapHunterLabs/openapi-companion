package dev.gaphunter.openapicompanion.example

import java.math.BigDecimal

/**
 * Format-neutral JSON value tree built from YAML or JSON PSI, so the
 * example checks are written once and unit-tested without PSI -- same split
 * as the unused-component check's `ComponentDeclaration`.
 *
 * [anchor] is where a finding on this value is shown; [keyAnchor] is the
 * key it sits under, if any -- a one-line highlight instead of a whole
 * multi-line object or array.
 */
sealed class SpecNode<out A>(val anchor: A, val keyAnchor: A?) {
    class Null<out A>(anchor: A, keyAnchor: A?) : SpecNode<A>(anchor, keyAnchor)
    class Bool<out A>(val value: Boolean, anchor: A, keyAnchor: A?) : SpecNode<A>(anchor, keyAnchor)
    class Num<out A>(val value: BigDecimal, anchor: A, keyAnchor: A?) : SpecNode<A>(anchor, keyAnchor) {
        val isIntegral: Boolean get() = value.signum() == 0 || value.stripTrailingZeros().scale() <= 0
    }
    class Str<out A>(val value: String, anchor: A, keyAnchor: A?) : SpecNode<A>(anchor, keyAnchor)
    class Arr<out A>(val items: List<SpecNode<A>>, anchor: A, keyAnchor: A?) : SpecNode<A>(anchor, keyAnchor)
    class Obj<out A>(val properties: Map<String, SpecNode<A>>, anchor: A, keyAnchor: A?) : SpecNode<A>(anchor, keyAnchor) {
        operator fun get(key: String): SpecNode<A>? = properties[key]
        fun obj(key: String): Obj<A>? = properties[key] as? Obj<A>
        fun arr(key: String): Arr<A>? = properties[key] as? Arr<A>
        fun str(key: String): String? = (properties[key] as? Str<A>)?.value
        fun num(key: String): BigDecimal? = (properties[key] as? Num<A>)?.value
        fun isTrue(key: String): Boolean = (properties[key] as? Bool<A>)?.value == true
    }

    /**
     * A value whose JSON type can't be pinned down -- a YAML scalar that
     * YAML 1.1 and 1.2 parsers read differently, a tag, an alias, a merge
     * key. Every check skips it: an unknown value is never reported.
     * [rawText] keeps the source text of a plain scalar (e.g. `3.1.0`).
     */
    class Unknown<out A>(val rawText: String?, anchor: A, keyAnchor: A?) : SpecNode<A>(anchor, keyAnchor)
}
