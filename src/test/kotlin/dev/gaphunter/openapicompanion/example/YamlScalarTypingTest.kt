package dev.gaphunter.openapicompanion.example

import dev.gaphunter.openapicompanion.example.YamlScalarTyping.Kind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class YamlScalarTypingTest {

    private fun kind(text: String) = YamlScalarTyping.classifyPlain(text)

    @Test
    fun nullsBothYamlVersionsAgreeOn() {
        for (text in listOf("", "~", "null", "Null", "NULL")) assertEquals(text, Kind.NullValue, kind(text))
    }

    @Test
    fun booleansBothYamlVersionsAgreeOn() {
        assertEquals(Kind.BoolValue(true), kind("true"))
        assertEquals(Kind.BoolValue(true), kind("TRUE"))
        assertEquals(Kind.BoolValue(false), kind("False"))
    }

    @Test
    fun yaml11OnlyBooleansAreAmbiguous() {
        for (text in listOf("yes", "No", "on", "OFF", "y", "n")) assertEquals(text, Kind.Ambiguous, kind(text))
    }

    @Test
    fun plainDecimalsAreNumbers() {
        assertEquals(Kind.NumberValue(BigDecimal("0")), kind("0"))
        assertEquals(Kind.NumberValue(BigDecimal("42")), kind("42"))
        assertEquals(Kind.NumberValue(BigDecimal("-7")), kind("-7"))
        assertEquals(Kind.NumberValue(BigDecimal("9.99")), kind("9.99"))
    }

    @Test
    fun numberLikeTextTheVersionsDisagreeOnIsAmbiguous() {
        val cases = listOf(
            "010", // octal 8 in 1.1, decimal 10 in 1.2
            "0x1F", "0o17", "1e5", "1_000", "+5", "1.",
            "2024-01-01", // timestamp in 1.1
            "12:30", // sexagesimal 750 in 1.1
            ".inf", ".NaN", "1.0.0", "-foo",
        )
        for (text in cases) assertEquals(text, Kind.Ambiguous, kind(text))
    }

    @Test
    fun mergeAndValueKeysAreAmbiguous() {
        assertEquals(Kind.Ambiguous, kind("<<"))
        assertEquals(Kind.Ambiguous, kind("="))
    }

    @Test
    fun everythingElseIsAString() {
        assertEquals(Kind.StringValue("Fluffy"), kind("Fluffy"))
        assertEquals(Kind.StringValue("available"), kind("available"))
        assertEquals(Kind.StringValue("yesterday"), kind("yesterday"))
        assertEquals(Kind.StringValue("v1.0.0"), kind("v1.0.0"))
    }
}
