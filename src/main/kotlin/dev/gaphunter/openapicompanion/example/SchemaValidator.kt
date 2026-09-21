package dev.gaphunter.openapicompanion.example

import java.math.BigDecimal

/** [at] is always a node of the example VALUE (never of the schema), so a
 * finding can be shown in the file being inspected even when the schema
 * came from another file. [path] locates it inside the value (`items[2].price`). */
class Violation<out A>(val at: SpecNode<A>, val path: String, val detail: String)

/**
 * Checks a value against an OpenAPI Schema Object, fail-closed: it reports
 * only what is DEFINITELY invalid, and skips anything it can't decide.
 *
 * Checked: `type` (including OAS 3.1 type arrays with `"null"`, OAS 3.0
 * `nullable` and Swagger 2.0 `x-nullable`), `enum`, `const` (OAS 3.1+),
 * `required`, `properties`, `items`, `minimum`/`maximum` (with both the
 * boolean and the numeric form of `exclusiveMinimum`/`exclusiveMaximum`),
 * `minLength`/`maxLength`.
 *
 * Skipped, never reported: a schema using `allOf`/`oneOf`/`anyOf`/`not`/
 * `if`-`then`-`else`, dynamic/recursive refs or `$id`/`$anchor`; a `$ref`
 * that doesn't resolve locally; an unknown `type`; an [SpecNode.Unknown]
 * value; a required property that is `readOnly`/`writeOnly` (an example
 * can legitimately leave it out, depending on its direction). `pattern`,
 * `format` and the other keywords aren't checked -- an unchecked keyword can
 * only hide a problem, never invent one.
 */
class SchemaValidator<A>(
    private val jsonSchemaSemantics: Boolean,
    private val resolveRef: (ref: String, from: SpecNode<A>) -> SpecNode<A>?,
    private val checkCanceled: () -> Unit = {},
) {
    companion object {
        private const val REF = "\$ref"
        private const val MAX_REF_HOPS = 32
        private const val MAX_ENUM_VALUES_SHOWN = 5
        private val KNOWN_TYPES = setOf("string", "number", "integer", "boolean", "object", "array", "null")
        private val FAIL_CLOSED_KEYWORDS = setOf(
            "allOf", "oneOf", "anyOf", "not", "if", "then", "else",
            "\$dynamicRef", "\$recursiveRef", "\$id", "\$anchor", "\$dynamicAnchor", "\$recursiveAnchor", "\$schema",
        )

        fun describe(label: String, violation: Violation<*>): String {
            val at = if (violation.path.isEmpty()) "" else " at '${violation.path}'"
            return "'$label' doesn't match its schema$at: ${violation.detail}"
        }

        private fun typeName(value: SpecNode<*>): String = when (value) {
            is SpecNode.Null -> "null"
            is SpecNode.Bool -> "boolean"
            is SpecNode.Num -> if (value.isIntegral) "integer" else "number"
            is SpecNode.Str -> "string"
            is SpecNode.Arr -> "array"
            is SpecNode.Obj -> "object"
            is SpecNode.Unknown -> "unknown"
        }

        private fun matchesType(value: SpecNode<*>, type: String): Boolean = when (type) {
            "null" -> value is SpecNode.Null
            "boolean" -> value is SpecNode.Bool
            "string" -> value is SpecNode.Str
            "object" -> value is SpecNode.Obj
            "array" -> value is SpecNode.Arr
            "number" -> value is SpecNode.Num
            "integer" -> value is SpecNode.Num && value.isIntegral
            else -> true
        }

        private fun format(number: BigDecimal): String =
            if (number.signum() == 0) "0" else number.stripTrailingZeros().toPlainString()

        private fun render(node: SpecNode<*>): String = when (node) {
            is SpecNode.Str -> "'${node.value}'"
            is SpecNode.Num -> format(node.value)
            is SpecNode.Bool -> node.value.toString()
            is SpecNode.Null -> "null"
            is SpecNode.Arr -> "[...]"
            is SpecNode.Obj -> "{...}"
            is SpecNode.Unknown -> node.rawText ?: "?"
        }

        /** JSON equality by value (numbers mathematically: 1 == 1.0). Null
         * when an [SpecNode.Unknown] makes it undecidable. */
        private fun jsonEquals(a: SpecNode<*>, b: SpecNode<*>): Boolean? = when {
            a is SpecNode.Unknown || b is SpecNode.Unknown -> null
            a is SpecNode.Null -> b is SpecNode.Null
            a is SpecNode.Bool -> b is SpecNode.Bool && a.value == b.value
            a is SpecNode.Num -> b is SpecNode.Num && a.value.compareTo(b.value) == 0
            a is SpecNode.Str -> b is SpecNode.Str && a.value == b.value
            a is SpecNode.Arr ->
                if (b !is SpecNode.Arr || a.items.size != b.items.size) false
                else allOf(a.items.zip(b.items).map { (x, y) -> jsonEquals(x, y) })
            a is SpecNode.Obj ->
                if (b !is SpecNode.Obj || a.properties.keys != b.properties.keys) false
                else allOf(a.properties.map { (key, x) -> jsonEquals(x, b.properties.getValue(key)) })
            else -> null
        }

        private fun allOf(results: List<Boolean?>): Boolean? = when {
            results.any { it == false } -> false
            results.any { it == null } -> null
            else -> true
        }

        private fun join(path: String, property: String) = if (path.isEmpty()) property else "$path.$property"
    }

    fun validate(value: SpecNode<A>, schema: SpecNode<A>): List<Violation<A>> {
        // A top-level null example/default is widely written to mean "none
        // given" rather than "the value null" -- never reported.
        if (value is SpecNode.Null) return emptyList()
        val out = mutableListOf<Violation<A>>()
        check(value, schema, "", 0, out)
        return out.distinctBy { Triple(it.at, it.path, it.detail) }
    }

    private fun check(value: SpecNode<A>, schemaNode: SpecNode<A>, path: String, refHops: Int, out: MutableList<Violation<A>>) {
        checkCanceled()
        if (value is SpecNode.Unknown) return
        val schema = schemaNode as? SpecNode.Obj<A> ?: return
        if (FAIL_CLOSED_KEYWORDS.any { it in schema.properties }) return
        if (value is SpecNode.Null && allowsNull(schema)) return

        if (REF in schema.properties) {
            val target = deref(schema, refHops) ?: return
            check(value, target, path, refHops + 1, out)
            // OAS 3.0 / Swagger 2.0: everything next to a $ref is ignored.
            if (!jsonSchemaSemantics) return
        }

        val types = typesOf(schema) ?: return
        if (types.isNotEmpty() && types.none { matchesType(value, it) }) {
            val expected = if (allowsNull(schema) && "null" !in types) types + "null" else types
            out += Violation(value, path, "expected ${expected.joinToString(" or ")}, found ${typeName(value)}")
            return
        }

        schema.arr("enum")?.items?.takeIf { it.isNotEmpty() }?.let { allowed ->
            if (allowed.all { jsonEquals(value, it) == false }) {
                val shown = allowed.take(MAX_ENUM_VALUES_SHOWN).joinToString(", ", transform = ::render) +
                    if (allowed.size > MAX_ENUM_VALUES_SHOWN) ", ..." else ""
                out += Violation(value, path, "${render(value)} is not one of the allowed values: $shown")
                return
            }
        }
        if (jsonSchemaSemantics) {
            schema["const"]?.let { constant ->
                if (jsonEquals(value, constant) == false) {
                    out += Violation(value, path, "expected the constant ${render(constant)}, found ${render(value)}")
                    return
                }
            }
        }

        when (value) {
            is SpecNode.Obj -> checkObject(value, schema, path, out)
            is SpecNode.Arr -> checkArray(value, schema, path, out)
            is SpecNode.Num -> checkNumber(value, schema, path, out)
            is SpecNode.Str -> checkString(value, schema, path, out)
            else -> {}
        }
    }

    private fun checkObject(value: SpecNode.Obj<A>, schema: SpecNode.Obj<A>, path: String, out: MutableList<Violation<A>>) {
        val properties = schema.obj("properties")
        val required = schema.arr("required")?.items
        if (required != null && required.all { it is SpecNode.Str }) {
            val missing = required.map { (it as SpecNode.Str).value }
                .filter { name -> name !in value.properties && !mayBeOmitted(properties?.get(name)) }
            if (missing.isNotEmpty()) {
                val names = missing.joinToString(", ") { "'$it'" }
                out += Violation(value, path, if (missing.size == 1) "missing required property $names" else "missing required properties $names")
            }
        }
        if (properties == null) return
        for ((name, child) in value.properties) {
            val childSchema = properties[name] ?: continue
            check(child, childSchema, join(path, name), 0, out)
        }
    }

    private fun checkArray(value: SpecNode.Arr<A>, schema: SpecNode.Obj<A>, path: String, out: MutableList<Violation<A>>) {
        // The tuple form (an array of schemas) and boolean schemas aren't checked.
        val items = schema.obj("items") ?: return
        value.items.forEachIndexed { index, item -> check(item, items, "$path[$index]", 0, out) }
    }

    private fun checkNumber(value: SpecNode.Num<A>, schema: SpecNode.Obj<A>, path: String, out: MutableList<Violation<A>>) {
        val number = value.value
        schema.num("minimum")?.let { minimum ->
            // OAS 3.0 / Swagger 2.0: exclusiveMinimum is a boolean modifier.
            val exclusive = schema.isTrue("exclusiveMinimum")
            if (number < minimum || (exclusive && number.compareTo(minimum) == 0)) {
                out += Violation(value, path, if (exclusive) "must be greater than ${format(minimum)}" else "below the minimum ${format(minimum)}")
            }
        }
        // OAS 3.1: exclusiveMinimum is the bound itself.
        schema.num("exclusiveMinimum")?.let { bound ->
            if (number <= bound) out += Violation(value, path, "must be greater than ${format(bound)}")
        }
        schema.num("maximum")?.let { maximum ->
            val exclusive = schema.isTrue("exclusiveMaximum")
            if (number > maximum || (exclusive && number.compareTo(maximum) == 0)) {
                out += Violation(value, path, if (exclusive) "must be less than ${format(maximum)}" else "above the maximum ${format(maximum)}")
            }
        }
        schema.num("exclusiveMaximum")?.let { bound ->
            if (number >= bound) out += Violation(value, path, "must be less than ${format(bound)}")
        }
    }

    private fun checkString(value: SpecNode.Str<A>, schema: SpecNode.Obj<A>, path: String, out: MutableList<Violation<A>>) {
        // JSON Schema counts characters as code points, not UTF-16 units.
        val length = BigDecimal(value.value.codePointCount(0, value.value.length))
        schema.num("minLength")?.let { minLength ->
            if (length < minLength) out += Violation(value, path, "shorter than minLength ${format(minLength)} (length ${format(length)})")
        }
        schema.num("maxLength")?.let { maxLength ->
            if (length > maxLength) out += Violation(value, path, "longer than maxLength ${format(maxLength)} (length ${format(length)})")
        }
    }

    private fun allowsNull(schema: SpecNode.Obj<A>): Boolean = schema.isTrue("nullable") || schema.isTrue("x-nullable")

    /** Null for a type keyword this can't reason about (`file`, a typo,
     * not a string) -- the whole schema is then skipped. */
    private fun typesOf(schema: SpecNode.Obj<A>): List<String>? {
        val types = when (val type = schema["type"]) {
            null -> return emptyList()
            is SpecNode.Str -> listOf(type.value)
            is SpecNode.Arr -> type.items.map { (it as? SpecNode.Str)?.value ?: return null }
            else -> return null
        }
        return types.takeIf { all -> all.all { it in KNOWN_TYPES } }
    }

    private fun deref(schema: SpecNode.Obj<A>, refHops: Int): SpecNode<A>? {
        if (refHops >= MAX_REF_HOPS) return null
        val ref = schema.str(REF) ?: return null
        return resolveRef(ref, schema)
    }

    /** A required property whose schema is readOnly/writeOnly (or can't be
     * inspected well enough to tell) is allowed to be missing. */
    private fun mayBeOmitted(propertySchema: SpecNode<A>?): Boolean {
        var current = propertySchema as? SpecNode.Obj<A> ?: return false
        repeat(MAX_REF_HOPS) {
            if (current.isTrue("readOnly") || current.isTrue("writeOnly")) return true
            if (FAIL_CLOSED_KEYWORDS.any { it in current.properties }) return true
            if (REF !in current.properties) return false
            current = deref(current, 0) as? SpecNode.Obj<A> ?: return true
        }
        return true
    }
}
