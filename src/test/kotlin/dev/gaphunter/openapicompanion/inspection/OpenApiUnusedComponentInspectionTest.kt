package dev.gaphunter.openapicompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Runs the real inspection through the platform's own highlighting pipeline
 * (`enableInspections` + `doHighlighting`) against the demo fixture directory
 * read from disk -- the headless equivalent of opening
 * `demo/unused-components/` in runIde, with no inline copy to drift. The
 * license is injected through the inspection's constructor, so no live
 * `LicensingFacade` is needed.
 */
class OpenApiUnusedComponentInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = File("demo").absolutePath

    private fun enable(licensed: Boolean) {
        myFixture.enableInspections(
            OpenApiYamlUnusedComponentInspection { licensed },
            OpenApiJsonUnusedComponentInspection { licensed },
        )
    }

    private fun flaggedNames(): Set<String> =
        myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Potentially unused component") }
            .map { it.substringAfter("'").substringBefore("'") }
            .toSet()

    private fun flaggedInDemo(file: String, licensed: Boolean = true): Set<String> {
        myFixture.copyDirectoryToProject("unused-components", "api")
        myFixture.configureFromTempProjectFile("api/$file")
        enable(licensed)
        return flaggedNames()
    }

    fun testFlagsExactlyTheUnusedComponentsOfTheYamlSpec() {
        // Everything else in that file is a trap: bare-name discriminator
        // mapping (Dog), implicit discriminator subtype (Cat), a use only
        // from a fragment file (Money), a use only from a JSON file
        // (Address), security schemes used and unused, and OrderItem, whose
        // name extends Order's.
        assertEquals(setOf("Order", "UnusedFilter", "LegacyError"), flaggedInDemo("openapi.yaml"))
    }

    fun testFlagsTheUnusedComponentOfTheJsonSpec() {
        assertEquals(setOf("StaleItem"), flaggedInDemo("inventory.json"))
    }

    fun testFlagsTheUnusedDefinitionOfTheSwagger2Spec() {
        assertEquals(setOf("DeprecatedUser"), flaggedInDemo("swagger2.yaml"))
    }

    fun testReportsNothingWithoutALicense() {
        assertEquals(emptySet<String>(), flaggedInDemo("openapi.yaml", licensed = false))
    }

    fun testIgnoresYamlThatIsNotAnOpenApiSpec() {
        myFixture.configureByText(
            "docker-compose.yml",
            """
            services:
              web:
                image: nginx
            components:
              schemas:
                Foo:
                  type: object
            """.trimIndent(),
        )
        enable(licensed = true)
        assertEquals(emptySet<String>(), flaggedNames())
    }

    fun testLargeSplitSpecFinishesAndFlagsExactlyTheUnreferencedHalf() {
        val schemaCount = 400
        val fileCount = 20
        val spec = buildString {
            append("openapi: 3.0.3\ncomponents:\n  schemas:\n")
            for (i in 0 until schemaCount) append("    Schema$i:\n      type: object\n")
        }
        myFixture.addFileToProject("perf/openapi.yaml", spec)
        for (f in 0 until fileCount) {
            val refs = (0 until schemaCount step 2).filter { (it / 2) % fileCount == f }
            myFixture.addFileToProject(
                "perf/refs-$f.yaml",
                refs.joinToString("\n", prefix = "refs:\n") { "  - ${'$'}ref: 'openapi.yaml#/components/schemas/Schema$it'" },
            )
        }
        myFixture.configureFromTempProjectFile("perf/openapi.yaml")
        enable(licensed = true)

        val started = System.nanoTime()
        val flagged = flaggedNames()
        val millis = (System.nanoTime() - started) / 1_000_000
        println("unused-component inspection: $schemaCount schemas across ${fileCount + 1} files in ${millis}ms")

        assertEquals((1 until schemaCount step 2).map { "Schema$it" }.toSet(), flagged)
    }
}
