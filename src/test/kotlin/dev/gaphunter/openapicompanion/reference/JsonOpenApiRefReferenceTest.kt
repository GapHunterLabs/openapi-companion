package dev.gaphunter.openapicompanion.reference

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Real PSI resolution across 2+ fixture files -- confirms `$ref`
 * resolves locally (same-file and cross-file) in a JSON-formatted
 * OpenAPI spec, that a genuinely missing target resolves to null
 * instead of throwing, that an http(s) `$ref` never resolves (this
 * plugin has no HTTP client anywhere), and -- since this plugin is
 * 100% Paid, no free tier, unlike every Freemium plugin elsewhere in
 * this catalog -- that resolution is unconditionally gated on a
 * license, tested directly via the injected [isLicensed] lambda rather
 * than a live LicensingFacade (never initialized in a test sandbox).
 */
class JsonOpenApiRefReferenceTest : BasePlatformTestCase() {

    private fun findRefStringLiteral(file: PsiFile): JsonStringLiteral {
        val jsonFile = file as JsonFile
        return PsiTreeUtil.findChildrenOfType(jsonFile, JsonStringLiteral::class.java)
            .first { literal ->
                val property = literal.parent as? JsonProperty
                property?.name == "\$ref" && property.value === literal
            }
    }

    fun testResolvesSameFilePointerToAComponentSchema() {
        myFixture.configureByText(
            "openapi.json",
            """
            {
              "openapi": "3.0.3",
              "paths": {
                "/pets": { "get": { "responses": { "200": { "content": { "application/json": {
                  "schema": { "${'$'}ref": "#/components/schemas/Pet" }
                } } } } } }
              },
              "components": {
                "schemas": {
                  "Pet": { "type": "object" }
                }
              }
            }
            """.trimIndent(),
        )
        val refLiteral = findRefStringLiteral(myFixture.file)
        val resolved = JsonOpenApiRefReference(refLiteral, isLicensed = { true }).resolve()
        assertNotNull("expected the \$ref to resolve", resolved)
        val property = resolved!!.parent as JsonProperty
        assertEquals("Pet", property.name)
    }

    fun testResolvesRefAcrossTwoFiles() {
        myFixture.addFileToProject(
            "schemas/pet.json",
            """
            { "Pet": { "type": "object" } }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "openapi.json",
            """
            {
              "openapi": "3.0.3",
              "components": {
                "schemas": {
                  "PetRef": { "${'$'}ref": "schemas/pet.json#/Pet" }
                }
              }
            }
            """.trimIndent(),
        )
        val refLiteral = findRefStringLiteral(myFixture.file)
        val resolved = JsonOpenApiRefReference(refLiteral, isLicensed = { true }).resolve()
        assertNotNull("expected the cross-file \$ref to resolve", resolved)
        val property = resolved!!.parent as JsonProperty
        assertEquals("Pet", property.name)
        assertEquals("pet.json", property.containingFile.name)
    }

    fun testDoesNotResolveWhenTargetIsMissing() {
        myFixture.configureByText(
            "openapi.json",
            """
            {
              "openapi": "3.0.3",
              "components": {
                "schemas": {
                  "PetRef": { "${'$'}ref": "#/components/schemas/DoesNotExist" }
                }
              }
            }
            """.trimIndent(),
        )
        val refLiteral = findRefStringLiteral(myFixture.file)
        assertNull(JsonOpenApiRefReference(refLiteral, isLicensed = { true }).resolve())
    }

    fun testNeverResolvesAnHttpRef() {
        myFixture.configureByText(
            "openapi.json",
            """
            {
              "openapi": "3.0.3",
              "components": {
                "schemas": {
                  "PetRef": { "${'$'}ref": "https://example.com/schemas/pet.json" }
                }
              }
            }
            """.trimIndent(),
        )
        val refLiteral = findRefStringLiteral(myFixture.file)
        assertNull(
            "an http(s) \$ref must never resolve via network access",
            JsonOpenApiRefReference(refLiteral, isLicensed = { true }).resolve(),
        )
    }

    fun testNeverResolvesWithoutALicenseEvenWhenTheTargetIsValid() {
        myFixture.configureByText(
            "openapi.json",
            """
            {
              "openapi": "3.0.3",
              "components": {
                "schemas": {
                  "PetRef": { "${'$'}ref": "#/components/schemas/Pet" },
                  "Pet": { "type": "object" }
                }
              }
            }
            """.trimIndent(),
        )
        val refLiteral = findRefStringLiteral(myFixture.file)
        assertNull(
            "100% Paid, no free tier -- must never resolve without a license, regardless of how valid the ref is",
            JsonOpenApiRefReference(refLiteral, isLicensed = { false }).resolve(),
        )
    }

    fun testDoesNotAttachOurReferenceOutsideARecognizedOpenApiFile() {
        myFixture.configureByText(
            "plain.json",
            """
            {
              "${'$'}ref": "#/definitions/User",
              "definitions": { "User": {} }
            }
            """.trimIndent(),
        )
        val refLiteral = findRefStringLiteral(myFixture.file)
        assertNull(JsonOpenApiRefUtil.asRefProperty(refLiteral))
    }
}
