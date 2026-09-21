package dev.gaphunter.openapicompanion.example

import java.math.BigDecimal

/**
 * JSON type of an unquoted (plain) YAML scalar -- only when YAML 1.1
 * (SnakeYAML and most Java OpenAPI tooling) and YAML 1.2 core (most
 * JavaScript tooling) agree on it. They disagree on a lot: `yes`/`no`/
 * `on`/`off` are booleans only in 1.1, `2024-01-01` is a timestamp only in
 * 1.1, `010` is octal 8 in 1.1 and decimal 10 in 1.2, `12:30` is the
 * sexagesimal integer 750 in 1.1, `1e5` is a float only in 1.2. Any of
 * those is [Ambiguous], and an ambiguous example value is never reported,
 * whatever the schema says.
 *
 * Quoted and block scalars are always strings and never come through here.
 */
object YamlScalarTyping {
    sealed interface Kind {
        object NullValue : Kind
        data class BoolValue(val value: Boolean) : Kind
        data class NumberValue(val value: BigDecimal) : Kind
        data class StringValue(val value: String) : Kind
        object Ambiguous : Kind
    }

    private val NULLS = setOf("", "~", "null", "Null", "NULL")
    private val TRUES = setOf("true", "True", "TRUE")
    private val FALSES = setOf("false", "False", "FALSE")
    private val YAML11_ONLY_BOOLS = setOf(
        "y", "Y", "yes", "Yes", "YES", "n", "N", "no", "No", "NO",
        "on", "On", "ON", "off", "Off", "OFF",
    )
    private val DECIMAL_INT = Regex("""-?(0|[1-9][0-9]*)""")
    private val SIMPLE_FLOAT = Regex("""-?(0|[1-9][0-9]*)\.[0-9]+""")
    private const val NUMBER_LIKE_STARTS = "0123456789+-."

    fun classifyPlain(text: String): Kind = when {
        text in NULLS -> Kind.NullValue
        text in TRUES -> Kind.BoolValue(true)
        text in FALSES -> Kind.BoolValue(false)
        text in YAML11_ONLY_BOOLS -> Kind.Ambiguous
        DECIMAL_INT.matches(text) || SIMPLE_FLOAT.matches(text) -> Kind.NumberValue(BigDecimal(text))
        // Everything else that starts like a number (octal, hex, exponents,
        // underscores, timestamps, sexagesimal, .inf/.nan, versions like
        // 1.0.0) or is a merge/value key: at least one YAML version reads it
        // as something other than a string, so don't guess.
        text[0] in NUMBER_LIKE_STARTS || text == "<<" || text == "=" -> Kind.Ambiguous
        else -> Kind.StringValue(text)
    }
}
