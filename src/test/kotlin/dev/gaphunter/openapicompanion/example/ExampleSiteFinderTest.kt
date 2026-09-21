package dev.gaphunter.openapicompanion.example

import dev.gaphunter.openapicompanion.detection.OpenApiDetector.SpecVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleSiteFinderTest {

    /** Each site as "label -> where the value is -> where its schema is". */
    private fun sites(spec: Map<String, Any?>, version: SpecVersion = SpecVersion.OPENAPI_3): List<String> =
        ExampleSiteFinder.find(node(spec), version).map { "${it.label} -> ${it.value.anchor} -> ${it.schema.anchor}" }

    @Test
    fun schemaExamplesAndDefaultsAtAnyDepth() {
        val spec = mapOf(
            "openapi" to "3.0.3",
            "components" to mapOf(
                "schemas" to mapOf(
                    "Pet" to mapOf(
                        "example" to mapOf("name" to "Rex"),
                        "properties" to mapOf("name" to mapOf("type" to "string", "default" to "Rex")),
                    ),
                ),
            ),
        )
        assertEquals(
            listOf(
                "example -> #/components/schemas/Pet/example -> #/components/schemas/Pet",
                "default -> #/components/schemas/Pet/properties/name/default -> #/components/schemas/Pet/properties/name",
            ),
            sites(spec),
        )
    }

    @Test
    fun aPropertyNamedExampleOrDefaultIsNotAnExample() {
        val spec = mapOf(
            "openapi" to "3.0.3",
            "components" to mapOf(
                "schemas" to mapOf(
                    "Doc" to mapOf(
                        "properties" to mapOf(
                            "example" to mapOf("type" to "string"),
                            "default" to mapOf("type" to "boolean"),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(emptyList<String>(), sites(spec))
    }

    @Test
    fun mediaTypeExamplesOnlyForJsonMediaTypes() {
        val content = mapOf(
            "application/json" to mapOf("schema" to mapOf("type" to "object"), "example" to mapOf<String, Any>()),
            "application/problem+json; charset=utf-8" to mapOf("schema" to mapOf("type" to "object"), "example" to mapOf<String, Any>()),
            "application/xml" to mapOf("schema" to mapOf("type" to "object"), "example" to "<a/>"),
            "application/x-ndjson" to mapOf("schema" to mapOf("type" to "object"), "example" to "{}\n{}"),
        )
        val spec = mapOf(
            "openapi" to "3.0.3",
            "paths" to mapOf("/a" to mapOf("get" to mapOf("responses" to mapOf("200" to mapOf("content" to content))))),
        )
        val labels = sites(spec)
        assertEquals(2, labels.size)
        assertTrue(labels.all { "json" in it })
    }

    @Test
    fun namedExamplesSkipRefsAndExternalValues() {
        val media = mapOf(
            "schema" to mapOf("type" to "object"),
            "examples" to mapOf(
                "inline" to mapOf("value" to mapOf("a" to 1)),
                "shared" to mapOf("\$ref" to "#/components/examples/Shared"),
                "remote" to mapOf("externalValue" to "https://example.com/a.json"),
            ),
        )
        val spec = mapOf(
            "openapi" to "3.0.3",
            "paths" to mapOf("/a" to mapOf("post" to mapOf("requestBody" to mapOf("content" to mapOf("application/json" to media))))),
        )
        assertEquals(
            listOf("examples.inline.value -> #/paths//a/post/requestBody/content/application/json/examples/inline/value -> #/paths//a/post/requestBody/content/application/json/schema"),
            sites(spec),
        )
    }

    @Test
    fun parameterAndHeaderExamplesAgainstTheirSchema() {
        val spec = mapOf(
            "openapi" to "3.0.3",
            "paths" to mapOf(
                "/a" to mapOf(
                    "parameters" to listOf(mapOf("name" to "id", "in" to "path", "schema" to mapOf("type" to "integer"), "example" to 0)),
                    "get" to mapOf(
                        "parameters" to listOf(mapOf("\$ref" to "#/components/parameters/Shared")),
                        "responses" to mapOf(
                            "200" to mapOf("headers" to mapOf("X-Rate" to mapOf("schema" to mapOf("type" to "integer"), "example" to "high"))),
                            "x-internal" to mapOf("content" to mapOf("application/json" to mapOf("schema" to mapOf<String, Any>(), "example" to 1))),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            listOf(
                "example -> #/paths//a/parameters/0/example -> #/paths//a/parameters/0/schema",
                "example -> #/paths//a/get/responses/200/headers/X-Rate/example -> #/paths//a/get/responses/200/headers/X-Rate/schema",
            ),
            sites(spec),
        )
    }

    @Test
    fun schemaLevelExamplesArrayOnlyInOas31() {
        val schemas = mapOf("Nick" to mapOf("type" to listOf("string", "null"), "examples" to listOf("Rex", 42)))
        assertEquals(emptyList<String>(), sites(mapOf("openapi" to "3.0.3", "components" to mapOf("schemas" to schemas))))
        assertEquals(
            listOf("examples[0] -> #/components/schemas/Nick/examples/0 -> #/components/schemas/Nick", "examples[1] -> #/components/schemas/Nick/examples/1 -> #/components/schemas/Nick"),
            sites(mapOf("openapi" to "3.1.0", "components" to mapOf("schemas" to schemas))),
        )
    }

    @Test
    fun detectsOas31FromAnyScalarForm() {
        assertTrue(ExampleSiteFinder.usesJsonSchemaSemantics(node(mapOf("openapi" to "3.1.0")), SpecVersion.OPENAPI_3))
        // `openapi: 3.1.0` unquoted in YAML is an ambiguous scalar, `3.1` a number.
        assertTrue(ExampleSiteFinder.usesJsonSchemaSemantics(SpecNode.Obj(mapOf("openapi" to SpecNode.Unknown("3.1.0", "#", null)), "#", null), SpecVersion.OPENAPI_3))
        assertTrue(ExampleSiteFinder.usesJsonSchemaSemantics(node(mapOf("openapi" to 3.1)), SpecVersion.OPENAPI_3))
        assertFalse(ExampleSiteFinder.usesJsonSchemaSemantics(node(mapOf("openapi" to "3.0.3")), SpecVersion.OPENAPI_3))
        assertFalse(ExampleSiteFinder.usesJsonSchemaSemantics(node(mapOf("swagger" to "2.0")), SpecVersion.SWAGGER_2))
    }

    @Test
    fun swagger2ParametersAndResponses() {
        val spec = mapOf(
            "swagger" to "2.0",
            "paths" to mapOf(
                "/users" to mapOf(
                    "get" to mapOf(
                        "parameters" to listOf(
                            mapOf("name" to "limit", "in" to "query", "type" to "integer", "default" to 500, "x-example" to 7),
                            mapOf("name" to "body", "in" to "body", "schema" to mapOf("type" to "object", "example" to mapOf<String, Any>())),
                        ),
                        "responses" to mapOf(
                            "200" to mapOf(
                                "schema" to mapOf("type" to "object"),
                                "examples" to mapOf("application/json" to mapOf("id" to 1), "text/plain" to "x"),
                                "headers" to mapOf("X-Rate" to mapOf("type" to "integer", "default" to 1)),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            listOf(
                "default -> #/paths//users/get/parameters/0/default -> #/paths//users/get/parameters/0",
                "example -> #/paths//users/get/parameters/1/schema/example -> #/paths//users/get/parameters/1/schema",
                "examples.application/json -> #/paths//users/get/responses/200/examples/application/json -> #/paths//users/get/responses/200/schema",
                "default -> #/paths//users/get/responses/200/headers/X-Rate/default -> #/paths//users/get/responses/200/headers/X-Rate",
            ),
            sites(spec, SpecVersion.SWAGGER_2),
        )
    }

    @Test
    fun jsonMediaTypes() {
        assertTrue(ExampleSiteFinder.isJsonMediaType("application/json"))
        assertTrue(ExampleSiteFinder.isJsonMediaType("Application/JSON; charset=utf-8"))
        assertTrue(ExampleSiteFinder.isJsonMediaType("application/vnd.api+json"))
        assertFalse(ExampleSiteFinder.isJsonMediaType("application/x-ndjson"))
        assertFalse(ExampleSiteFinder.isJsonMediaType("application/jsonl"))
        assertFalse(ExampleSiteFinder.isJsonMediaType("text/plain"))
    }
}
