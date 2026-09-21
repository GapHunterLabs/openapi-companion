package dev.gaphunter.openapicompanion.usage

/**
 * Pure rules for deciding whether a component is referenced: how its
 * pointer is written, how to spot that pointer in raw text, and which
 * schemas count as used through discriminator semantics alone.
 */
object ComponentUsageMatcher {
    private val SCHEMA_SECTIONS = setOf("components/schemas", "definitions")

    /** Characters that can legitimately end a complete pointer inside a spec file. */
    private val POINTER_TERMINATORS = setOf('\'', '"', '/', ',', ']', '}')

    /** RFC 6901: `~` -> `~0` before `/` -> `~1` -- the exact inverse of the unescaping in `YamlPointer`/`JsonPointer`. */
    fun escapeSegment(name: String): String = name.replace("~", "~0").replace("/", "~1")

    fun pointerOf(section: String, name: String): String = "#/$section/${escapeSegment(name)}"

    /**
     * The pointer as written, plus its percent-encoded form when that
     * differs -- a pointer inside a URI fragment may be percent-encoded
     * (RFC 6901 section 6), e.g. `Order%20Item` for a name with a space.
     */
    fun needles(pointer: String): List<String> {
        val encoded = percentEncode(pointer)
        return if (encoded == pointer) listOf(pointer) else listOf(pointer, encoded)
    }

    /**
     * True if [needle] occurs in [text] as a whole pointer. `.../Order`
     * inside `.../OrderItem` doesn't count; `.../Order/properties/id` does,
     * since a reference into a component uses that component.
     */
    fun containsPointer(text: CharSequence, needle: String): Boolean {
        var from = 0
        while (true) {
            val at = text.indexOf(needle, from)
            if (at < 0) return false
            val next = at + needle.length
            if (next == text.length || text[next].isWhitespace() || text[next] in POINTER_TERMINATORS) return true
            from = at + 1
        }
    }

    /**
     * Pointers of schemas that are used through discriminator semantics
     * alone, never through a written-out pointer -- the false-positive trap
     * behind openapi-generator issue #4193:
     * - a bare schema name in `discriminator.mapping` (`dog: Dog`); a full
     *   pointer there is plain text the project-wide search already finds;
     * - an implicit subtype: a schema whose `allOf` points at a schema that
     *   declares a `discriminator`.
     *
     * An `allOf` pointing into ANOTHER file counts as a subtype without
     * checking the target, since this file alone can't tell whether that
     * target declares a discriminator. That can hide a genuinely unused
     * schema, but never flags a used one.
     */
    fun <A> usedByDiscriminator(declarations: List<ComponentDeclaration<A>>): Set<String> {
        val schemas = declarations.filter { it.section in SCHEMA_SECTIONS }
        val schemaSection = schemas.firstOrNull()?.section ?: return emptySet()
        val parents = schemas.filter { it.hasDiscriminator }.map { pointerOf(it.section, it.name) }.toSet()

        val bareMappingNames = schemas
            .flatMap { it.discriminatorMappingValues }
            .filter { '#' !in it && '/' !in it }
            .map { pointerOf(schemaSection, it) }
        val implicitSubtypes = schemas
            .filter { schema -> schema.allOfRefs.any { ref -> !ref.startsWith("#") || ref in parents } }
            .map { pointerOf(it.section, it.name) }
        return (bareMappingNames + implicitSubtypes).toSet()
    }

    private fun percentEncode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (code < 0x80 && (char.isLetterOrDigit() || char in "-._~/#")) append(char) else append("%%%02X".format(code))
        }
    }
}
