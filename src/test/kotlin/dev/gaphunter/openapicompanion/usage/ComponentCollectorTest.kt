package dev.gaphunter.openapicompanion.usage

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gaphunter.openapicompanion.detection.OpenApiDetector.SpecVersion

class ComponentCollectorTest : BasePlatformTestCase() {

    private fun collect(fileName: String, text: String, version: SpecVersion = SpecVersion.OPENAPI_3) =
        ComponentCollector.collect(myFixture.configureByText(fileName, text), version)

    fun testCollectsEveryRefableOas3SectionInYaml() {
        val declarations = collect(
            "openapi.yaml",
            """
            openapi: 3.1.0
            components:
              schemas: {S: {}}
              responses: {R: {}}
              parameters: {P: {}}
              examples: {E: {}}
              requestBodies: {B: {}}
              headers: {H: {}}
              links: {L: {}}
              callbacks: {C: {}}
              securitySchemes: {Auth: {}}
              pathItems: {Item: {}}
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                "components/schemas" to "S", "components/responses" to "R", "components/parameters" to "P",
                "components/examples" to "E", "components/requestBodies" to "B", "components/headers" to "H",
                "components/links" to "L", "components/callbacks" to "C",
            ),
            declarations.map { it.section to it.name },
        )
    }

    fun testCollectsTheSameSectionsInJson() {
        val declarations = collect(
            "openapi.json",
            """
            {"openapi": "3.0.3", "components": {
              "schemas": {"S": {}}, "parameters": {"P": {}}, "securitySchemes": {"Auth": {}}
            }}
            """.trimIndent(),
        )
        assertEquals(
            listOf("components/schemas" to "S", "components/parameters" to "P"),
            declarations.map { it.section to it.name },
        )
    }

    fun testSwagger2CoversDefinitionsOnly() {
        val declarations = collect(
            "swagger.yaml",
            """
            swagger: '2.0'
            definitions: {User: {}}
            parameters: {Limit: {}}
            responses: {NotFound: {}}
            """.trimIndent(),
            SpecVersion.SWAGGER_2,
        )
        assertEquals(listOf("definitions" to "User"), declarations.map { it.section to it.name })
    }

    fun testReadsDiscriminatorDataFromYamlSchemas() {
        val declarations = collect(
            "openapi.yaml",
            """
            openapi: 3.0.3
            components:
              schemas:
                Pet:
                  discriminator:
                    propertyName: petType
                    mapping:
                      dog: Dog
                      cat: '#/components/schemas/Cat'
                Dog:
                  allOf:
                    - ${'$'}ref: '#/components/schemas/Pet'
                    - type: object
            """.trimIndent(),
        )
        val pet = declarations.single { it.name == "Pet" }
        assertTrue(pet.hasDiscriminator)
        assertEquals(listOf("Dog", "#/components/schemas/Cat"), pet.discriminatorMappingValues)
        assertEquals(listOf("#/components/schemas/Pet"), declarations.single { it.name == "Dog" }.allOfRefs)
    }

    fun testReadsDiscriminatorDataFromJsonSchemas() {
        val declarations = collect(
            "openapi.json",
            """
            {"openapi": "3.0.3", "components": {"schemas": {
              "Pet": {"discriminator": {"propertyName": "petType", "mapping": {"dog": "Dog"}}},
              "Dog": {"allOf": [{"${'$'}ref": "#/components/schemas/Pet"}, {"type": "object"}]}
            }}}
            """.trimIndent(),
        )
        assertEquals(listOf("Dog"), declarations.single { it.name == "Pet" }.discriminatorMappingValues)
        assertEquals(listOf("#/components/schemas/Pet"), declarations.single { it.name == "Dog" }.allOfRefs)
    }

    fun testSwagger2StringDiscriminatorStillCounts() {
        val declarations = collect(
            "swagger.yaml",
            """
            swagger: '2.0'
            definitions:
              Pet:
                discriminator: petType
            """.trimIndent(),
            SpecVersion.SWAGGER_2,
        )
        assertTrue(declarations.single().hasDiscriminator)
    }

    fun testAnchorsOnTheComponentKey() {
        val declarations = collect("openapi.yaml", "openapi: 3.0.3\ncomponents:\n  schemas:\n    Order:\n      type: object\n")
        assertEquals("Order", declarations.single().anchor.text)
    }
}
