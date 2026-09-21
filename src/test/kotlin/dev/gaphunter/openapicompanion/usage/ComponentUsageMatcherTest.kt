package dev.gaphunter.openapicompanion.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentUsageMatcherTest {

    private fun schema(
        name: String,
        hasDiscriminator: Boolean = false,
        mapping: List<String> = emptyList(),
        allOf: List<String> = emptyList(),
        section: String = "components/schemas",
    ) = ComponentDeclaration(section, name, Unit, hasDiscriminator, mapping, allOf)

    @Test
    fun escapesTildeBeforeSlashPerRfc6901() {
        assertEquals("a~1b~0c", ComponentUsageMatcher.escapeSegment("a/b~c"))
        // "~1" as a literal name must not be confused with an escaped "/".
        assertEquals("~01", ComponentUsageMatcher.escapeSegment("~1"))
    }

    @Test
    fun buildsTheSamePointerARefWouldUse() {
        assertEquals("#/components/schemas/Order", ComponentUsageMatcher.pointerOf("components/schemas", "Order"))
        assertEquals("#/definitions/User", ComponentUsageMatcher.pointerOf("definitions", "User"))
    }

    @Test
    fun matchesAWholePointerInEveryQuotingStyle() {
        val needle = "#/components/schemas/Order"
        assertTrue(ComponentUsageMatcher.containsPointer("\$ref: '#/components/schemas/Order'", needle))
        assertTrue(ComponentUsageMatcher.containsPointer("{\"\$ref\": \"#/components/schemas/Order\"}", needle))
        assertTrue(ComponentUsageMatcher.containsPointer("\$ref: '../openapi.yaml#/components/schemas/Order'", needle))
        assertTrue(ComponentUsageMatcher.containsPointer("#/components/schemas/Order", needle))
    }

    @Test
    fun aRefIntoAComponentUsesThatComponent() {
        assertTrue(ComponentUsageMatcher.containsPointer("'#/components/schemas/Order/properties/id'", "#/components/schemas/Order"))
    }

    @Test
    fun aLongerNameIsNotAUseOfTheShorterOne() {
        val needle = "#/components/schemas/Order"
        assertFalse(ComponentUsageMatcher.containsPointer("'#/components/schemas/OrderItem'", needle))
        assertFalse(ComponentUsageMatcher.containsPointer("'#/components/schemas/Order.v2'", needle))
        assertFalse(ComponentUsageMatcher.containsPointer("'#/components/schemas/Order-v2'", needle))
        assertFalse(ComponentUsageMatcher.containsPointer("'#/components/schemas/Order~1x'", needle))
    }

    @Test
    fun keepsScanningPastAnOccurrenceThatIsOnlyAPrefix() {
        val text = "a: '#/components/schemas/OrderItem'\nb: '#/components/schemas/Order'"
        assertTrue(ComponentUsageMatcher.containsPointer(text, "#/components/schemas/Order"))
    }

    @Test
    fun addsAPercentEncodedNeedleOnlyWhenItDiffers() {
        assertEquals(listOf("#/components/schemas/Order"), ComponentUsageMatcher.needles("#/components/schemas/Order"))
        assertEquals(
            listOf("#/components/schemas/Order Item", "#/components/schemas/Order%20Item"),
            ComponentUsageMatcher.needles("#/components/schemas/Order Item"),
        )
        assertEquals(
            listOf("#/components/schemas/Größe", "#/components/schemas/Gr%C3%B6%C3%9Fe"),
            ComponentUsageMatcher.needles("#/components/schemas/Größe"),
        )
    }

    @Test
    fun bareNameInADiscriminatorMappingIsAUse() {
        val used = ComponentUsageMatcher.usedByDiscriminator(
            listOf(schema("Pet", hasDiscriminator = true, mapping = listOf("Dog")), schema("Dog")),
        )
        assertTrue("#/components/schemas/Dog" in used)
    }

    @Test
    fun fullPointerMappingIsLeftToTheProjectSearch() {
        val used = ComponentUsageMatcher.usedByDiscriminator(
            listOf(schema("Pet", hasDiscriminator = true, mapping = listOf("#/components/schemas/Dog")), schema("Dog")),
        )
        assertFalse("#/components/schemas/Dog" in used)
    }

    @Test
    fun implicitDiscriminatorSubtypesAreUsed() {
        // The exact shape from openapi-generator #4193: Cat and Lizard are only
        // subtypes by allOf, never named in a mapping.
        val used = ComponentUsageMatcher.usedByDiscriminator(
            listOf(
                schema("Pet", hasDiscriminator = true, mapping = listOf("Dog")),
                schema("Cat", allOf = listOf("#/components/schemas/Pet")),
                schema("Dog", allOf = listOf("#/components/schemas/Pet")),
                schema("Lizard", allOf = listOf("#/components/schemas/Pet")),
            ),
        )
        assertEquals(
            setOf("#/components/schemas/Cat", "#/components/schemas/Dog", "#/components/schemas/Lizard"),
            used,
        )
    }

    @Test
    fun allOfWithoutADiscriminatorParentIsNotAUse() {
        val used = ComponentUsageMatcher.usedByDiscriminator(
            listOf(schema("Base"), schema("Extended", allOf = listOf("#/components/schemas/Base"))),
        )
        assertTrue(used.isEmpty())
    }

    @Test
    fun crossFileAllOfFailsClosed() {
        val used = ComponentUsageMatcher.usedByDiscriminator(
            listOf(schema("Dog", allOf = listOf("common.yaml#/components/schemas/Pet"))),
        )
        assertEquals(setOf("#/components/schemas/Dog"), used)
    }

    @Test
    fun swagger2DefinitionsGetTheSameSemantics() {
        val used = ComponentUsageMatcher.usedByDiscriminator(
            listOf(
                schema("Pet", hasDiscriminator = true, section = "definitions"),
                schema("Cat", allOf = listOf("#/definitions/Pet"), section = "definitions"),
            ),
        )
        assertEquals(setOf("#/definitions/Cat"), used)
    }

    @Test
    fun nonSchemaSectionsNeverCountAsDiscriminatorUses() {
        val used = ComponentUsageMatcher.usedByDiscriminator(
            listOf(ComponentDeclaration("components/parameters", "Dog", Unit, hasDiscriminator = true)),
        )
        assertTrue(used.isEmpty())
    }
}
