package dev.gaphunter.openapicompanion.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecPointerTest {

    @Test
    fun splitsRefsIntoFileAndPointer() {
        assertEquals(SpecPointer.Ref("", "/components/schemas/Pet"), SpecPointer.splitRef("#/components/schemas/Pet"))
        assertEquals(SpecPointer.Ref("schemas/pet.yaml", ""), SpecPointer.splitRef("schemas/pet.yaml"))
        assertEquals(SpecPointer.Ref("common.json", "/Pet"), SpecPointer.splitRef("common.json#/Pet"))
    }

    @Test
    fun decodesPercentEncodingButNotPlus() {
        assertEquals("/components/schemas/Order Item", SpecPointer.splitRef("#/components/schemas/Order%20Item")?.pointer)
        assertEquals("/components/schemas/Größe", SpecPointer.splitRef("#/components/schemas/Gr%C3%B6%C3%9Fe")?.pointer)
        assertEquals("/a+b", SpecPointer.splitRef("#/a+b")?.pointer)
    }

    @Test
    fun refusesWhatItCannotResolve() {
        assertNull("plain-name anchor", SpecPointer.splitRef("#Pet"))
        assertNull("broken percent-encoding", SpecPointer.splitRef("#/a%2"))
        assertNull("broken percent-encoding", SpecPointer.splitRef("#/a%zz"))
    }

    @Test
    fun resolvesToTheValueThroughObjectsAndArrays() {
        val root = node(mapOf("a" to mapOf("list" to listOf("x", mapOf("b" to 1)))))
        assertEquals("#/a/list/1/b", SpecPointer.resolve(root, "/a/list/1/b")?.anchor)
        assertEquals("#", SpecPointer.resolve(root, "")?.anchor)
        assertNull(SpecPointer.resolve(root, "/a/missing"))
        assertNull(SpecPointer.resolve(root, "/a/list/9"))
    }

    @Test
    fun unescapesSlashBeforeTilde() {
        val root = node(mapOf("a/b" to 1, "~1" to 2))
        assertEquals("#/a/b", SpecPointer.resolve(root, "/a~1b")?.anchor)
        assertEquals("#/~1", SpecPointer.resolve(root, "/~01")?.anchor)
    }
}
