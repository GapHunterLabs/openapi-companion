package dev.gaphunter.openapicompanion.reference

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * YAML counterpart of [JsonOpenApiRefReferenceTest] -- OpenAPI specs
 * are written in YAML at least as often as JSON in real-world use, and
 * this is a separate PSI backend ([org.jetbrains.yaml.psi]), not just a
 * different syntax for the same resolver, so it needs its own real-PSI
 * coverage, not an assumption that "the JSON tests passing means YAML
 * works too."
 */
class YamlOpenApiRefReferenceTest : BasePlatformTestCase() {

    private fun findRefScalar(file: PsiFile): YAMLScalar {
        val yamlFile = file as YAMLFile
        return PsiTreeUtil.findChildrenOfType(yamlFile, YAMLScalar::class.java)
            .first { scalar ->
                val keyValue = scalar.parent as? YAMLKeyValue
                keyValue?.keyText == "\$ref" && keyValue.value === scalar
            }
    }

    fun testResolvesSameFilePointerToAComponentSchema() {
        myFixture.configureByText(
            "openapi.yaml",
            """
            openapi: 3.0.3
            paths:
              /pets:
                get:
                  responses:
                    '200':
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/Pet'
            components:
              schemas:
                Pet:
                  type: object
            """.trimIndent(),
        )
        val refScalar = findRefScalar(myFixture.file)
        val resolved = YamlOpenApiRefReference(refScalar, isLicensed = { true }).resolve()
        assertNotNull("expected the \$ref to resolve", resolved)
        val keyValue = resolved!!.parent as YAMLKeyValue
        assertEquals("Pet", keyValue.keyText)
    }

    fun testResolvesRefAcrossTwoFiles() {
        myFixture.addFileToProject(
            "schemas/pet.yaml",
            """
            Pet:
              type: object
            """.trimIndent(),
        )
        myFixture.configureByText(
            "openapi.yaml",
            """
            openapi: 3.0.3
            components:
              schemas:
                PetRef:
                  ${'$'}ref: 'schemas/pet.yaml#/Pet'
            """.trimIndent(),
        )
        val refScalar = findRefScalar(myFixture.file)
        val resolved = YamlOpenApiRefReference(refScalar, isLicensed = { true }).resolve()
        assertNotNull("expected the cross-file \$ref to resolve", resolved)
        val keyValue = resolved!!.parent as YAMLKeyValue
        assertEquals("Pet", keyValue.keyText)
        assertEquals("pet.yaml", keyValue.containingFile.name)
    }

    fun testDoesNotResolveWhenTargetIsMissing() {
        myFixture.configureByText(
            "openapi.yaml",
            """
            openapi: 3.0.3
            components:
              schemas:
                PetRef:
                  ${'$'}ref: '#/components/schemas/DoesNotExist'
            """.trimIndent(),
        )
        val refScalar = findRefScalar(myFixture.file)
        assertNull(YamlOpenApiRefReference(refScalar, isLicensed = { true }).resolve())
    }

    fun testNeverResolvesAnHttpRef() {
        myFixture.configureByText(
            "openapi.yaml",
            """
            openapi: 3.0.3
            components:
              schemas:
                PetRef:
                  ${'$'}ref: 'https://example.com/schemas/pet.yaml'
            """.trimIndent(),
        )
        val refScalar = findRefScalar(myFixture.file)
        assertNull(
            "an http(s) \$ref must never resolve via network access",
            YamlOpenApiRefReference(refScalar, isLicensed = { true }).resolve(),
        )
    }

    fun testNeverResolvesWithoutALicenseEvenWhenTheTargetIsValid() {
        myFixture.configureByText(
            "openapi.yaml",
            """
            openapi: 3.0.3
            components:
              schemas:
                PetRef:
                  ${'$'}ref: '#/components/schemas/Pet'
                Pet:
                  type: object
            """.trimIndent(),
        )
        val refScalar = findRefScalar(myFixture.file)
        assertNull(
            "100% Paid, no free tier -- must never resolve without a license, regardless of how valid the ref is",
            YamlOpenApiRefReference(refScalar, isLicensed = { false }).resolve(),
        )
    }

    fun testDoesNotAttachOurReferenceOutsideARecognizedOpenApiFile() {
        myFixture.configureByText(
            "plain.yaml",
            """
            ${'$'}ref: '#/definitions/User'
            definitions:
              User: {}
            """.trimIndent(),
        )
        val refScalar = findRefScalar(myFixture.file)
        assertNull(YamlOpenApiRefUtil.asRefKeyValue(refScalar))
    }
}
