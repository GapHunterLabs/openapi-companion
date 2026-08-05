package dev.gaphunter.openapicompanion.reference

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * [OpenApiJsonSchemaGtdSuppressor] only has one real branch of logic
 * worth testing without a live IDE sandbox (see `KNOWN_ISSUES.md`
 * Round 1 for why this exists at all): it must never suppress the
 * bundled handler for an unlicensed user, since this plugin's own
 * references never resolve without a license either -- suppressing
 * unconditionally would leave an unlicensed user with nothing at all
 * where the bundled navigation used to work.
 */
class OpenApiJsonSchemaGtdSuppressorTest : BasePlatformTestCase() {

    fun testNeverSuppressesWithoutALicenseEvenOnARecognizedOpenApiFile() {
        val file = myFixture.configureByText(
            "openapi.yaml",
            """
            openapi: 3.0.3
            components:
              schemas:
                Pet:
                  type: object
            """.trimIndent(),
        )
        val suppressor = OpenApiJsonSchemaGtdSuppressor(isLicensed = { false })
        assertFalse(
            "must never suppress the bundled handler for an unlicensed user -- " +
                "this plugin's own references won't resolve either, so suppressing " +
                "would leave the user with nothing",
            suppressor.shouldSuppressGtd(file),
        )
    }

    fun testSuppressesOnARecognizedOpenApiFileWhenLicensed() {
        val file = myFixture.configureByText(
            "openapi.yaml",
            """
            openapi: 3.0.3
            components:
              schemas:
                Pet:
                  type: object
            """.trimIndent(),
        )
        val suppressor = OpenApiJsonSchemaGtdSuppressor(isLicensed = { true })
        assertTrue(suppressor.shouldSuppressGtd(file))
    }

    fun testDoesNotSuppressOutsideARecognizedOpenApiFileEvenWhenLicensed() {
        val file = myFixture.configureByText(
            "plain.yaml",
            """
            some: value
            """.trimIndent(),
        )
        val suppressor = OpenApiJsonSchemaGtdSuppressor(isLicensed = { true })
        assertFalse(suppressor.shouldSuppressGtd(file))
    }
}
