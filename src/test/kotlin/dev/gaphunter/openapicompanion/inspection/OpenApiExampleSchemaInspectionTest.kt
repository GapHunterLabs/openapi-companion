package dev.gaphunter.openapicompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Runs the example check through the platform's real highlighting pipeline
 * against `demo/example-validation/` read from disk -- same approach as
 * [OpenApiUnusedComponentInspectionTest]. Each expected message is exact,
 * so a finding that moves to the wrong value or path fails the test; and
 * every "SILENT" trap in the demo files is covered by the exact-set
 * comparison.
 */
class OpenApiExampleSchemaInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = File("demo").absolutePath

    private fun findingsIn(file: String, licensed: Boolean = true): Set<String> {
        myFixture.copyDirectoryToProject("example-validation", "api")
        myFixture.configureFromTempProjectFile("api/$file")
        myFixture.enableInspections(
            OpenApiYamlExampleSchemaInspection { licensed },
            OpenApiJsonExampleSchemaInspection { licensed },
        )
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { "doesn't match its schema" in it }
            .toSet()
    }

    /** Each finding with the exact source text it highlights. */
    private fun highlightedText(file: String): Map<String, String> {
        findingsIn(file)
        return myFixture.doHighlighting()
            .filter { it.description?.contains("doesn't match its schema") == true }
            .associate { it.description!! to it.text }
    }

    fun testOas30YamlSpec() {
        assertEquals(
            setOf(
                "'example' doesn't match its schema: below the minimum 1",
                "'example' doesn't match its schema: 'shipped' is not one of the allowed values: 'available', 'pending', 'sold'",
                "'examples.fluffy.value' doesn't match its schema at 'age': expected integer, found string",
                "'examples.fluffy.value' doesn't match its schema at 'tags[1]': expected string, found integer",
                "'example' doesn't match its schema: missing required property 'petId'",
                "'example' doesn't match its schema at 'address.zip': expected string, found integer",
                "'example' doesn't match its schema: longer than maxLength 10 (length 22)",
                "'default' doesn't match its schema: expected number, found string",
            ),
            findingsIn("openapi.yaml"),
        )
    }

    fun testHighlightsTheOffendingValueOrTheKeyOfAnObject() {
        val highlighted = highlightedText("openapi.yaml")
        assertEquals("old", highlighted["'examples.fluffy.value' doesn't match its schema at 'age': expected integer, found string"])
        assertEquals("150101", highlighted["'example' doesn't match its schema at 'address.zip': expected string, found integer"])
        // A missing property is reported on the object, highlighted by its key.
        assertEquals("example", highlighted["'example' doesn't match its schema: missing required property 'petId'"])
    }

    fun testOas31YamlSpec() {
        assertEquals(
            setOf(
                "'examples[2]' doesn't match its schema: expected string or null, found integer",
                "'example' doesn't match its schema: expected the constant 'v2', found 'v1'",
                "'default' doesn't match its schema: must be greater than 0",
            ),
            findingsIn("openapi31.yaml"),
        )
    }

    fun testSwagger2Spec() {
        assertEquals(
            setOf(
                "'default' doesn't match its schema: above the maximum 100",
                "'examples.application/json' doesn't match its schema: missing required property 'id'",
            ),
            findingsIn("swagger2.yaml"),
        )
    }

    fun testJsonSpec() {
        assertEquals(
            setOf(
                "'example' doesn't match its schema at 'sku': shorter than minLength 3 (length 2)",
                "'example' doesn't match its schema at 'stock': expected integer, found number",
            ),
            findingsIn("inventory.json"),
        )
    }

    fun testFragmentFilesAreNotSpecsAndAreNotChecked() {
        assertEquals(emptySet<String>(), findingsIn("schemas/order.yaml"))
    }

    fun testReportsNothingWithoutALicense() {
        assertEquals(emptySet<String>(), findingsIn("openapi.yaml", licensed = false))
    }

    fun testIgnoresYamlThatIsNotAnOpenApiSpec() {
        myFixture.configureByText(
            "values.yml",
            """
            components:
              schemas:
                Foo:
                  type: integer
                  example: not-a-number
            """.trimIndent(),
        )
        myFixture.enableInspections(OpenApiYamlExampleSchemaInspection { true })
        assertEquals(emptyList<String>(), myFixture.doHighlighting().mapNotNull { it.description }.filter { "doesn't match" in it })
    }
}
