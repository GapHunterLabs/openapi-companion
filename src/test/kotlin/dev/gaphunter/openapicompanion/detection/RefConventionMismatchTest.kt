package dev.gaphunter.openapicompanion.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefConventionMismatchTest {

    @Test
    fun `swagger 2-0 definitions ref inside an OpenAPI 3-x document is flagged`() {
        val message = RefConventionMismatch.describe("#/definitions/Pet", OpenApiDetector.SpecVersion.OPENAPI_3)
        assertTrue(message!!.contains("Swagger 2.0"))
        assertTrue(message.contains("#/components/schemas/"))
    }

    @Test
    fun `openapi 3-x components-schemas ref inside a Swagger 2-0 document is flagged`() {
        val message = RefConventionMismatch.describe("#/components/schemas/Pet", OpenApiDetector.SpecVersion.SWAGGER_2)
        assertTrue(message!!.contains("OpenAPI 3.x"))
        assertTrue(message.contains("#/definitions/"))
    }

    @Test
    fun `the correct convention for the document's own version is not flagged`() {
        assertNull(RefConventionMismatch.describe("#/components/schemas/Pet", OpenApiDetector.SpecVersion.OPENAPI_3))
        assertNull(RefConventionMismatch.describe("#/definitions/Pet", OpenApiDetector.SpecVersion.SWAGGER_2))
    }

    @Test
    fun `a cross-file ref carries the mismatch check on its fragment part only`() {
        val message = RefConventionMismatch.describe("./other.yaml#/definitions/Pet", OpenApiDetector.SpecVersion.OPENAPI_3)
        assertTrue(message!!.contains("Swagger 2.0"))
    }

    @Test
    fun `a ref with no fragment at all is never flagged`() {
        assertNull(RefConventionMismatch.describe("./pet.yaml", OpenApiDetector.SpecVersion.OPENAPI_3))
    }

    @Test
    fun `an unrelated fragment shape is never flagged`() {
        assertNull(RefConventionMismatch.describe("#/paths/~1pets", OpenApiDetector.SpecVersion.OPENAPI_3))
    }
}
