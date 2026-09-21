package dev.gaphunter.openapicompanion.example

import org.junit.Assert.assertEquals
import org.junit.Test

class SchemaValidatorTest {

    /** Findings as "path: detail" (just "detail" at the top level); `$ref`s
     * resolve against `#/defs/...` in the same tree. */
    private fun check(value: Any?, schema: Any?, oas31: Boolean = false, defs: Map<String, Any?> = emptyMap()): List<String> {
        val root = node(mapOf("defs" to defs, "schema" to schema, "value" to value)) as SpecNode.Obj
        val validator = SchemaValidator<String>(oas31, { ref, _ ->
            SpecPointer.splitRef(ref)?.takeIf { it.filePart.isEmpty() }?.let { SpecPointer.resolve(root, it.pointer) }
        })
        return validator.validate(root["value"]!!, root["schema"]!!)
            .map { if (it.path.isEmpty()) it.detail else "${it.path}: ${it.detail}" }
    }

    @Test
    fun reportsATypeMismatch() {
        assertEquals(listOf("expected integer, found string"), check("abc", mapOf("type" to "integer")))
        assertEquals(listOf("expected string, found integer"), check(5, mapOf("type" to "string")))
        assertEquals(listOf("expected integer, found number"), check(1.5, mapOf("type" to "integer")))
    }

    @Test
    fun anIntegralNumberIsAnInteger() {
        assertEquals(emptyList<String>(), check(5.0, mapOf("type" to "integer")))
        assertEquals(emptyList<String>(), check(5, mapOf("type" to "number")))
    }

    @Test
    fun aTopLevelNullIsNeverReported() {
        assertEquals(emptyList<String>(), check(null, mapOf("type" to "string")))
    }

    @Test
    fun aNestedNullIsReportedUnlessNullable() {
        val schema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "a" to mapOf("type" to "string"),
                "b" to mapOf("type" to "string", "nullable" to true),
                "c" to mapOf("type" to "string", "x-nullable" to true),
            ),
        )
        assertEquals(listOf("a: expected string, found null"), check(mapOf("a" to null, "b" to null, "c" to null), schema))
    }

    @Test
    fun nullableShowsUpInTheExpectedTypes() {
        assertEquals(listOf("expected string or null, found integer"), check(1, mapOf("type" to "string", "nullable" to true)))
    }

    @Test
    fun oas31TypeArraysAreUnions() {
        val schema = mapOf("type" to listOf("string", "null"))
        assertEquals(emptyList<String>(), check("x", schema, oas31 = true))
        assertEquals(
            listOf("x: expected string or null, found integer"),
            check(mapOf("x" to 42), mapOf("properties" to mapOf("x" to schema)), oas31 = true),
        )
    }

    @Test
    fun unknownTypeNamesSkipTheWholeSchema() {
        assertEquals(emptyList<String>(), check("x", mapOf("type" to "file")))
        assertEquals(emptyList<String>(), check("x", mapOf("type" to listOf("string", null))))
    }

    @Test
    fun enumIsComparedByValue() {
        val schema = mapOf("enum" to listOf("available", "pending", "sold"))
        assertEquals(emptyList<String>(), check("pending", schema))
        assertEquals(listOf("'shipped' is not one of the allowed values: 'available', 'pending', 'sold'"), check("shipped", schema))
        assertEquals(emptyList<String>(), check(1.0, mapOf("enum" to listOf(1, 2))))
    }

    @Test
    fun enumWithAnUndecidableValueIsSkipped() {
        assertEquals(emptyList<String>(), check("maybe", mapOf("enum" to listOf(UnknownValue, "no"))))
        assertEquals(emptyList<String>(), check(UnknownValue, mapOf("enum" to listOf("a"))))
    }

    @Test
    fun longEnumsAreTruncatedInTheMessage() {
        assertEquals(
            listOf("7 is not one of the allowed values: 1, 2, 3, 4, 5, ..."),
            check(7, mapOf("enum" to listOf(1, 2, 3, 4, 5, 6))),
        )
    }

    @Test
    fun constOnlyAppliesInOas31() {
        assertEquals(listOf("expected the constant 'v2', found 'v1'"), check("v1", mapOf("const" to "v2"), oas31 = true))
        assertEquals(emptyList<String>(), check("v1", mapOf("const" to "v2"), oas31 = false))
    }

    @Test
    fun reportsMissingRequiredPropertiesOnTheObject() {
        val schema = mapOf("type" to "object", "required" to listOf("id", "name", "age"))
        assertEquals(listOf("missing required properties 'id', 'age'"), check(mapOf("name" to "x"), schema))
    }

    @Test
    fun readOnlyAndWriteOnlyRequiredPropertiesMayBeMissing() {
        val schema = mapOf(
            "type" to "object",
            "required" to listOf("id", "password", "name"),
            "properties" to mapOf(
                "id" to mapOf("\$ref" to "#/defs/Id"),
                "password" to mapOf("type" to "string", "writeOnly" to true),
                "name" to mapOf("type" to "string"),
            ),
        )
        val defs = mapOf("Id" to mapOf("type" to "integer", "readOnly" to true))
        assertEquals(listOf("missing required property 'name'"), check(emptyMap<String, Any>(), schema, defs = defs))
    }

    @Test
    fun aRequiredPropertyWhoseSchemaCantBeInspectedMayBeMissing() {
        val schema = mapOf(
            "required" to listOf("a", "b"),
            "properties" to mapOf(
                "a" to mapOf("allOf" to listOf(mapOf("\$ref" to "#/defs/Id"))),
                "b" to mapOf("\$ref" to "#/defs/Missing"),
            ),
        )
        assertEquals(emptyList<String>(), check(emptyMap<String, Any>(), schema))
    }

    @Test
    fun descendsIntoPropertiesAndItemsWithAReadablePath() {
        val schema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "tags" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                "owner" to mapOf("properties" to mapOf("age" to mapOf("type" to "integer"))),
            ),
        )
        assertEquals(
            listOf("tags[1]: expected string, found integer", "owner.age: expected integer, found string"),
            check(mapOf("tags" to listOf("a", 3), "owner" to mapOf("age" to "old")), schema),
        )
    }

    @Test
    fun reportsOnTheOffendingValueItself() {
        val root = node(mapOf("schema" to mapOf("properties" to mapOf("age" to mapOf("type" to "integer"))), "value" to mapOf("age" to "old"))) as SpecNode.Obj
        val violations = SchemaValidator<String>(false, { _, _ -> null }).validate(root["value"]!!, root["schema"]!!)
        assertEquals(listOf("#/value/age"), violations.map { it.at.anchor })
    }

    @Test
    fun minimumAndMaximumIncludingBothExclusiveForms() {
        assertEquals(listOf("below the minimum 1"), check(0, mapOf("minimum" to 1)))
        assertEquals(listOf("above the maximum 100"), check(500, mapOf("maximum" to 100)))
        assertEquals(emptyList<String>(), check(1, mapOf("minimum" to 1, "maximum" to 1)))
        // OAS 3.0 / Swagger 2.0 boolean modifier
        assertEquals(listOf("must be greater than 0"), check(0, mapOf("minimum" to 0, "exclusiveMinimum" to true)))
        assertEquals(listOf("must be less than 10"), check(10, mapOf("maximum" to 10, "exclusiveMaximum" to true)))
        // OAS 3.1 numeric bound
        assertEquals(listOf("must be greater than 0"), check(0, mapOf("exclusiveMinimum" to 0)))
        assertEquals(emptyList<String>(), check(0.5, mapOf("exclusiveMinimum" to 0)))
    }

    @Test
    fun stringLengthCountsCodePoints() {
        assertEquals(listOf("longer than maxLength 3 (length 4)"), check("abcd", mapOf("maxLength" to 3)))
        assertEquals(listOf("shorter than minLength 3 (length 2)"), check("ab", mapOf("minLength" to 3)))
        // One emoji is one character, even though it's two UTF-16 units.
        assertEquals(emptyList<String>(), check("🐶", mapOf("maxLength" to 1)))
    }

    @Test
    fun followsRefs() {
        val defs = mapOf("Age" to mapOf("type" to "integer"), "Alias" to mapOf("\$ref" to "#/defs/Age"))
        assertEquals(listOf("expected integer, found string"), check("old", mapOf("\$ref" to "#/defs/Alias"), defs = defs))
    }

    @Test
    fun anUnresolvableRefIsSkipped() {
        assertEquals(emptyList<String>(), check("old", mapOf("\$ref" to "https://example.com/age.yaml")))
        assertEquals(emptyList<String>(), check("old", mapOf("\$ref" to "#/defs/Nope")))
    }

    @Test
    fun aRefCycleTerminatesWithoutFindings() {
        val defs = mapOf("A" to mapOf("\$ref" to "#/defs/B"), "B" to mapOf("\$ref" to "#/defs/A"))
        assertEquals(emptyList<String>(), check("x", mapOf("\$ref" to "#/defs/A"), defs = defs))
    }

    @Test
    fun keywordsNextToARefCountOnlyInOas31() {
        val defs = mapOf("Count" to mapOf("type" to "integer"))
        val schema = mapOf("\$ref" to "#/defs/Count", "maximum" to 10)
        assertEquals(emptyList<String>(), check(50, schema, oas31 = false, defs = defs))
        assertEquals(listOf("above the maximum 10"), check(50, schema, oas31 = true, defs = defs))
    }

    @Test
    fun nullableNextToARefAllowsNullInEitherVersion() {
        val defs = mapOf("Address" to mapOf("type" to "object"))
        val schema = mapOf("properties" to mapOf("a" to mapOf("\$ref" to "#/defs/Address", "nullable" to true)))
        assertEquals(emptyList<String>(), check(mapOf("a" to null), schema, defs = defs))
    }

    @Test
    fun compositionAndDynamicKeywordsFailClosed() {
        for (keyword in listOf("allOf", "oneOf", "anyOf")) {
            assertEquals(keyword, emptyList<String>(), check(true, mapOf("type" to "string", keyword to listOf(mapOf("type" to "string")))))
        }
        for (keyword in listOf("not", "if", "\$dynamicRef", "\$id")) {
            assertEquals(keyword, emptyList<String>(), check(true, mapOf("type" to "string", keyword to mapOf<String, Any>())))
        }
    }

    @Test
    fun unknownValuesAreNeverReported() {
        assertEquals(emptyList<String>(), check(UnknownValue, mapOf("type" to "integer")))
        assertEquals(
            emptyList<String>(),
            check(mapOf("a" to UnknownValue), mapOf("properties" to mapOf("a" to mapOf("type" to "integer")))),
        )
    }

    @Test
    fun describesAFindingWithItsLabelAndPath() {
        val root = node(mapOf("schema" to mapOf("properties" to mapOf("age" to mapOf("type" to "integer"))), "value" to mapOf("age" to "old"))) as SpecNode.Obj
        val violation = SchemaValidator<String>(false, { _, _ -> null }).validate(root["value"]!!, root["schema"]!!).single()
        assertEquals(
            "'examples.fluffy.value' doesn't match its schema at 'age': expected integer, found string",
            SchemaValidator.describe("examples.fluffy.value", violation),
        )
    }
}
