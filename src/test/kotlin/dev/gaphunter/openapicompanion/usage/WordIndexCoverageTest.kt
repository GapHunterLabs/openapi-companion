package dev.gaphunter.openapicompanion.usage

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Pins down the one platform behavior the unused-component search is
 * built on: the word index covers text INSIDE YAML/JSON string values,
 * so `processCandidateFilesForText` finds a file whose only mention of a
 * component is within a quoted `$ref` pointer. If a platform update ever
 * breaks this, this test fails first -- not the inspection, silently.
 */
class WordIndexCoverageTest : BasePlatformTestCase() {

    private fun candidates(text: String): Set<String> {
        val found = mutableSetOf<String>()
        PsiSearchHelper.getInstance(project).processCandidateFilesForText(
            GlobalSearchScope.projectScope(project),
            UsageSearchContext.ANY,
            true,
            text,
        ) { virtualFile ->
            found += virtualFile.name
            true
        }
        return found
    }

    fun testFindsAYamlFileWhoseOnlyMentionIsInsideAQuotedRef() {
        myFixture.addFileToProject(
            "api/uses.yaml",
            """
            paths:
              /orders:
                get:
                  responses:
                    '200':
                      ${'$'}ref: '#/components/schemas/Order'
            """.trimIndent(),
        )
        myFixture.addFileToProject("api/unrelated.yaml", "name: something else\n")
        assertEquals(setOf("uses.yaml"), candidates("#/components/schemas/Order"))
    }

    fun testFindsAJsonFileWhoseOnlyMentionIsInsideARefString() {
        myFixture.addFileToProject("api/uses.json", """{"${'$'}ref": "#/components/schemas/Order"}""")
        myFixture.addFileToProject("api/unrelated.json", """{"name": "something else"}""")
        assertEquals(setOf("uses.json"), candidates("#/components/schemas/Order"))
    }

    fun testFindsACrossFileRefWithARelativePathPrefix() {
        myFixture.addFileToProject(
            "api/schemas/pet.yaml",
            """
            Pet:
              properties:
                price:
                  ${'$'}ref: '../openapi.yaml#/components/schemas/Money'
            """.trimIndent(),
        )
        assertEquals(setOf("pet.yaml"), candidates("#/components/schemas/Money"))
    }

    fun testRequiresEveryWordNotJustTheComponentName() {
        myFixture.addFileToProject("api/mentions-name-only.yaml", "description: every Order is final\n")
        assertTrue(candidates("#/components/schemas/Order").isEmpty())
    }
}
