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

    /**
     * Every other test above constructs [YamlOpenApiRefReference] directly,
     * bypassing [YamlOpenApiRefReferenceContributor] entirely -- so none of
     * them would catch a wiring bug (wrong `language=` in plugin.xml, a
     * contributor that never gets registered, etc). This one goes through
     * the real extension pipeline via `getReferenceAtCaretPosition`, the
     * same path Ctrl+Click/Ctrl+B use in the IDE -- but only asserts that
     * OUR reference type gets attached, not that it resolves: the
     * contributor always uses the real (non-injected) [CheckLicense],
     * which returns null here (no live `LicensingFacade` in a headless
     * test), so resolution itself is proven separately below via the
     * same scalar with `isLicensed = { true }` injected directly.
     */
    fun testContributedReferenceIsAttachedThroughTheRealExtensionPipeline() {
        myFixture.configureByText(
            "openapi.yaml",
            """
            openapi: 3.0.3
            paths:
              /orders/{orderId}:
                get:
                  responses:
                    '200':
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/Or<caret>der'
            components:
              schemas:
                Order:
                  type: object
            """.trimIndent(),
        )
        val reference = myFixture.getReferenceAtCaretPosition()
        assertNotNull("expected a reference to be contributed at the caret", reference)
        assertTrue(
            "expected our own contributor's reference type, not some other plugin's",
            reference is YamlOpenApiRefReference,
        )

        val refScalar = findRefScalar(myFixture.file)
        val resolved = YamlOpenApiRefReference(refScalar, isLicensed = { true }).resolve()
        assertNotNull("expected the same scalar to resolve given a license", resolved)
        val keyValue = resolved!!.parent as YAMLKeyValue
        assertEquals("Order", keyValue.keyText)
    }

    /**
     * Byte-for-byte the real `demo/openapi.yaml` content shipped with this
     * plugin (the file the user actually opens in the runIde sandbox to
     * demo Ctrl+B navigation), not a simplified fixture -- to rule out a
     * real bug that only shows up with this file's specific shape (deeper
     * nesting, a preceding cross-file `$ref`, a broken `$ref` later in the
     * file) before assuming the failure is purely an IDE/interaction issue.
     * Same caveat as [testContributedReferenceIsAttachedThroughTheRealExtensionPipeline]
     * on why resolution is checked via direct construction, not through
     * the contributor's own (real, unlicensed-in-tests) reference.
     */
    fun testResolvesInTheRealDemoFileUnchanged() {
        myFixture.configureByText(
            "openapi.yaml",
            """
            openapi: 3.0.3
            info:
              title: Acme Corp Orders API
              version: 1.4.0
              description: >
                Order management for api.acme-corp.com -- create, look up, and
                cancel customer orders.

            servers:
              - url: https://api.acme-corp.com/v1

            paths:
              /orders/{orderId}:
                get:
                  summary: Fetch a single order
                  operationId: getOrder
                  parameters:
                    - name: orderId
                      in: path
                      required: true
                      schema:
                        type: string
                  responses:
                    '200':
                      description: The requested order
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/Or<caret>der'
                    '404':
                      description: No such order
                      content:
                        application/json:
                          schema:
                            # Cross-file reference -- resolves into schemas/error.yaml.
                            ${'$'}ref: './schemas/error.yaml#/ApiError'

              /orders/{orderId}/cancel:
                post:
                  summary: Cancel an order
                  operationId: cancelOrder
                  parameters:
                    - name: orderId
                      in: path
                      required: true
                      schema:
                        type: string
                  responses:
                    '200':
                      description: Order canceled
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/Order'
                    '409':
                      description: Order already shipped, cannot cancel
                      content:
                        application/json:
                          schema:
                            # Intentionally broken for the demo -- CancellationError was
                            # renamed to ApiError and this ${'$'}ref was never updated. Flagged
                            # with a warning instead of silently pointing nowhere.
                            ${'$'}ref: '#/components/schemas/CancellationError'

            components:
              schemas:
                Order:
                  type: object
                  required:
                    - id
                    - status
                    - customerEmail
                  properties:
                    id:
                      type: string
                      example: ord_8f3a2b1c
                    status:
                      type: string
                      enum: [pending, shipped, canceled]
                    customerEmail:
                      type: string
                      format: email
                    lineItems:
                      type: array
                      items:
                        ${'$'}ref: '#/components/schemas/LineItem'

                LineItem:
                  type: object
                  required:
                    - sku
                    - quantity
                  properties:
                    sku:
                      type: string
                      example: SKU-4471
                    quantity:
                      type: integer
                      minimum: 1
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "schemas/error.yaml",
            """
            ApiError:
              type: object
            """.trimIndent(),
        )
        val reference = myFixture.getReferenceAtCaretPosition()
        assertNotNull("expected a reference to be contributed at the caret, exactly like the real demo file", reference)
        assertTrue(
            "expected our own contributor's reference type, not some other plugin's",
            reference is YamlOpenApiRefReference,
        )

        val refScalar = findRefScalar(myFixture.file)
        val resolved = YamlOpenApiRefReference(refScalar, isLicensed = { true }).resolve()
        assertNotNull("expected the real demo file's first \$ref to resolve to the Order schema, given a license", resolved)
        val keyValue = resolved!!.parent as YAMLKeyValue
        assertEquals("Order", keyValue.keyText)
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
