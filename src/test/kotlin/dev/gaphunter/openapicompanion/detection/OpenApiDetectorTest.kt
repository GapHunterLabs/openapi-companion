package dev.gaphunter.openapicompanion.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenApiDetectorTest {
    @Test
    fun `detects an OpenAPI 3 x spec by its top-level key`() {
        assertEquals(
            OpenApiDetector.SpecVersion.OPENAPI_3,
            OpenApiDetector.detect("openapi: 3.0.3\ninfo:\n  title: Pet Store"),
        )
        assertEquals(
            OpenApiDetector.SpecVersion.OPENAPI_3,
            OpenApiDetector.detect("""{"openapi": "3.1.0", "info": {}}"""),
        )
    }

    @Test
    fun `detects a Swagger 2 0 spec by its top-level key`() {
        assertEquals(
            OpenApiDetector.SpecVersion.SWAGGER_2,
            OpenApiDetector.detect("swagger: '2.0'\ninfo:\n  title: Pet Store"),
        )
    }

    @Test
    fun `a plain JSON or YAML file with no version key is not recognized`() {
        assertNull(OpenApiDetector.detect("""{"name": "Acme", "version": "1.0"}"""))
        assertNull(OpenApiDetector.detect("name: Acme\nversion: '1.0'"))
    }

    @Test
    fun `a package json mentioning openapi in an unrelated field is not recognized`() {
        // Real false-positive risk: a Node project's package.json might list an
        // "openapi-generator" dependency, but that's not itself a spec.
        assertNull(OpenApiDetector.detect("""{"dependencies": {"openapi-generator-cli": "^3.0"}}"""))
    }
}
