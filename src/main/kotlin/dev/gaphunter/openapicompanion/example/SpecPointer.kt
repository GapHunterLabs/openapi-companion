package dev.gaphunter.openapicompanion.example

import java.io.ByteArrayOutputStream

/**
 * `$ref` handling on the format-neutral [SpecNode] tree: split a ref into
 * its file and pointer parts, and walk an RFC 6901 pointer. Resolving a
 * pointer through an object lands on the property's VALUE (the schema
 * itself), unlike the go-to-definition resolvers, which land on the key.
 */
object SpecPointer {

    data class Ref(val filePart: String, val pointer: String)

    /** Null for anything that isn't a plain `file#/pointer` ref: a plain-name
     * anchor (`#Pet`) can't be resolved without `$id`/`$anchor` support. */
    fun splitRef(ref: String): Ref? {
        val hash = ref.indexOf('#')
        val filePart = if (hash >= 0) ref.substring(0, hash) else ref
        val pointer = percentDecode(if (hash >= 0) ref.substring(hash + 1) else "") ?: return null
        if (pointer.isNotEmpty() && !pointer.startsWith("/")) return null
        return Ref(filePart, pointer)
    }

    fun <A> resolve(root: SpecNode<A>, pointer: String): SpecNode<A>? {
        if (pointer.isEmpty()) return root
        var current: SpecNode<A> = root
        for (rawSegment in pointer.substring(1).split("/")) {
            val segment = rawSegment.replace("~1", "/").replace("~0", "~")
            current = when (val node = current) {
                is SpecNode.Obj -> node[segment] ?: return null
                is SpecNode.Arr -> node.items.getOrNull(segment.toIntOrNull() ?: return null) ?: return null
                else -> return null
            }
        }
        return current
    }

    /** `%XX` sequences decoded as UTF-8, `+` left alone (it isn't a space in
     * a URI fragment). Null when a `%` isn't followed by two hex digits. */
    private fun percentDecode(text: String): String? {
        if ('%' !in text) return text
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            if (text[i] == '%') {
                if (i + 2 >= text.length) return null
                val hi = Character.digit(text[i + 1], 16)
                val lo = Character.digit(text[i + 2], 16)
                if (hi < 0 || lo < 0) return null
                bytes.write(hi * 16 + lo)
                i += 3
            } else {
                // Whole run up to the next '%', so surrogate pairs encode intact.
                val end = text.indexOf('%', i).let { if (it < 0) text.length else it }
                val run = text.substring(i, end).toByteArray(Charsets.UTF_8)
                bytes.write(run, 0, run.size)
                i = end
            }
        }
        return bytes.toString(Charsets.UTF_8)
    }
}
