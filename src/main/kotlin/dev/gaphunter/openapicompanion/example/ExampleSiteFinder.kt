package dev.gaphunter.openapicompanion.example

import dev.gaphunter.openapicompanion.detection.OpenApiDetector.SpecVersion

/** One value to check: [value] must be valid against [schema] (which may
 * itself be a `$ref`). [label] names it in the message: `example`,
 * `default`, `examples.cat.value`, ... */
class ExampleSite<out A>(val label: String, val value: SpecNode<A>, val schema: SpecNode<A>)

/**
 * Finds every inline example and default in a spec by walking the real
 * OpenAPI structure -- never by looking for any mapping that happens to
 * have an `example` key, so a PROPERTY named `example` or `default` is
 * never mistaken for one.
 *
 * Covered, the same places Spectral's `oas3-valid-schema-example` /
 * `oas3-valid-media-example` / `oas2-valid-schema-example` /
 * `oas2-valid-media-example` look:
 * - Schema Objects anywhere (components, parameters, bodies, responses,
 *   nested properties/items/compositions): `example`, `default`, and in
 *   OAS 3.1+ the JSON Schema `examples` array.
 * - Parameters and headers: `example` / `examples.*.value` against their
 *   `schema`.
 * - Media types: `example` / `examples.*.value` against their `schema`,
 *   JSON media types only -- an XML or plain-text example is a string by
 *   design, whatever the schema describes.
 * - Swagger 2.0: `default` on non-body parameters and headers (the
 *   parameter itself is the schema there), `examples.<json mime>` on
 *   responses.
 *
 * Skipped on purpose: anything behind a `$ref` here (a referenced
 * parameter/response/example is walked, or not, where it's declared --
 * and a finding can only be shown in the file being inspected) and
 * `externalValue` (never fetched).
 */
class ExampleSiteFinder<A> private constructor(
    private val oas31: Boolean,
    private val checkCanceled: () -> Unit,
) {
    private val sites = mutableListOf<ExampleSite<A>>()

    companion object {
        private const val REF = "\$ref"
        private val OAS3_METHODS = listOf("get", "put", "post", "delete", "options", "head", "patch", "trace")
        private val SWAGGER2_METHODS = listOf("get", "put", "post", "delete", "options", "head", "patch")
        private val SUBSCHEMA_KEYS = listOf(
            "items", "additionalProperties", "not", "if", "then", "else", "contains", "propertyNames",
            "additionalItems", "unevaluatedItems", "unevaluatedProperties",
        )
        private val SUBSCHEMA_MAP_KEYS = listOf("properties", "patternProperties", "\$defs", "definitions", "dependentSchemas")
        private val SUBSCHEMA_LIST_KEYS = listOf("allOf", "oneOf", "anyOf", "prefixItems")
        private val OAS31_OR_LATER = Regex("""^3\.[1-9]""")

        fun <A> find(root: SpecNode<A>, version: SpecVersion, checkCanceled: () -> Unit = {}): List<ExampleSite<A>> {
            val obj = root as? SpecNode.Obj<A> ?: return emptyList()
            val finder = ExampleSiteFinder<A>(usesJsonSchemaSemantics(root, version), checkCanceled)
            if (version == SpecVersion.OPENAPI_3) finder.walkOas3(obj) else finder.walkSwagger2(obj)
            return finder.sites
        }

        /** OAS 3.1+ schemas are full JSON Schema 2020-12: type arrays with
         * `"null"`, `const`, `examples` arrays, keywords next to `$ref`. */
        fun usesJsonSchemaSemantics(root: SpecNode<*>, version: SpecVersion): Boolean {
            if (version != SpecVersion.OPENAPI_3) return false
            val openapi = (root as? SpecNode.Obj<*>)?.get("openapi") ?: return false
            val text = when (openapi) {
                is SpecNode.Str -> openapi.value
                is SpecNode.Num -> openapi.value.toPlainString()
                is SpecNode.Unknown -> openapi.rawText
                else -> null
            } ?: return false
            return OAS31_OR_LATER.containsMatchIn(text)
        }

        /** `application/json`, `application/problem+json`, ... -- not
         * `application/x-ndjson` or `jsonl`, whose examples are strings. */
        fun isJsonMediaType(mediaType: String): Boolean {
            val base = mediaType.substringBefore(';').trim().lowercase()
            return base == "application/json" || base.endsWith("+json")
        }
    }

    private fun add(label: String, value: SpecNode<A>, schema: SpecNode<A>) {
        sites += ExampleSite(label, value, schema)
    }

    private fun SpecNode.Obj<A>.isRef(): Boolean = REF in properties

    private fun SpecNode.Obj<A>.nonExtensionValues(): List<SpecNode<A>> =
        properties.filterKeys { !it.startsWith("x-") }.values.toList()

    // --- OpenAPI 3.x ---------------------------------------------------------

    private fun walkOas3(root: SpecNode.Obj<A>) {
        root.obj("paths")?.nonExtensionValues()?.forEach(::walkPathItem)
        root.obj("webhooks")?.properties?.values?.forEach(::walkPathItem)
        val components = root.obj("components") ?: return
        components.obj("schemas")?.properties?.values?.forEach(::walkSchema)
        components.obj("parameters")?.properties?.values?.forEach(::walkParameter)
        components.obj("headers")?.properties?.values?.forEach(::walkParameter)
        components.obj("requestBodies")?.properties?.values?.forEach(::walkContentHolder)
        components.obj("responses")?.properties?.values?.forEach(::walkResponse)
        components.obj("pathItems")?.properties?.values?.forEach(::walkPathItem)
    }

    private fun walkPathItem(node: SpecNode<A>) {
        checkCanceled()
        val item = node as? SpecNode.Obj<A> ?: return
        if (item.isRef()) return
        item.arr("parameters")?.items?.forEach(::walkParameter)
        for (method in OAS3_METHODS) {
            val operation = item.obj(method) ?: continue
            operation.arr("parameters")?.items?.forEach(::walkParameter)
            operation["requestBody"]?.let(::walkContentHolder)
            operation.obj("responses")?.nonExtensionValues()?.forEach(::walkResponse)
            operation.obj("callbacks")?.properties?.values?.forEach { callback ->
                (callback as? SpecNode.Obj<A>)?.takeUnless { it.isRef() }?.nonExtensionValues()?.forEach(::walkPathItem)
            }
        }
    }

    /** Parameter and Header Objects share this shape. */
    private fun walkParameter(node: SpecNode<A>) {
        val parameter = node as? SpecNode.Obj<A> ?: return
        if (parameter.isRef()) return
        parameter["schema"]?.let { schema ->
            walkSchema(schema)
            addExamples(parameter, schema)
        }
        parameter.obj("content")?.let(::walkContent)
    }

    private fun walkContentHolder(node: SpecNode<A>) {
        val holder = node as? SpecNode.Obj<A> ?: return
        if (holder.isRef()) return
        holder.obj("content")?.let(::walkContent)
    }

    private fun walkResponse(node: SpecNode<A>) {
        val response = node as? SpecNode.Obj<A> ?: return
        if (response.isRef()) return
        response.obj("content")?.let(::walkContent)
        response.obj("headers")?.properties?.values?.forEach(::walkParameter)
    }

    private fun walkContent(content: SpecNode.Obj<A>) {
        for ((mediaType, node) in content.properties) {
            val media = node as? SpecNode.Obj<A> ?: continue
            val schema = media["schema"] ?: continue
            walkSchema(schema)
            if (isJsonMediaType(mediaType)) addExamples(media, schema)
        }
    }

    private fun addExamples(holder: SpecNode.Obj<A>, schema: SpecNode<A>) {
        holder["example"]?.let { add("example", it, schema) }
        holder.obj("examples")?.properties?.forEach { (name, node) ->
            val example = node as? SpecNode.Obj<A> ?: return@forEach
            if (example.isRef()) return@forEach
            example["value"]?.let { add("examples.$name.value", it, schema) }
        }
    }

    private fun walkSchema(node: SpecNode<A>) {
        checkCanceled()
        val schema = node as? SpecNode.Obj<A> ?: return
        schema["example"]?.let { add("example", it, schema) }
        schema["default"]?.let { add("default", it, schema) }
        if (oas31) schema.arr("examples")?.items?.forEachIndexed { i, value -> add("examples[$i]", value, schema) }

        for (key in SUBSCHEMA_KEYS) {
            when (val sub = schema[key]) {
                is SpecNode.Obj -> walkSchema(sub)
                is SpecNode.Arr -> if (key == "items") sub.items.forEach(::walkSchema)
                else -> {}
            }
        }
        for (key in SUBSCHEMA_MAP_KEYS) schema.obj(key)?.properties?.values?.forEach(::walkSchema)
        for (key in SUBSCHEMA_LIST_KEYS) schema.arr(key)?.items?.forEach(::walkSchema)
    }

    // --- Swagger 2.0 -----------------------------------------------------------

    private fun walkSwagger2(root: SpecNode.Obj<A>) {
        root.obj("definitions")?.properties?.values?.forEach(::walkSchema)
        root.obj("parameters")?.properties?.values?.forEach(::walkParameter2)
        root.obj("responses")?.properties?.values?.forEach(::walkResponse2)
        root.obj("paths")?.nonExtensionValues()?.forEach { node ->
            checkCanceled()
            val item = node as? SpecNode.Obj<A> ?: return@forEach
            if (item.isRef()) return@forEach
            item.arr("parameters")?.items?.forEach(::walkParameter2)
            for (method in SWAGGER2_METHODS) {
                val operation = item.obj(method) ?: continue
                operation.arr("parameters")?.items?.forEach(::walkParameter2)
                operation.obj("responses")?.nonExtensionValues()?.forEach(::walkResponse2)
            }
        }
    }

    private fun walkParameter2(node: SpecNode<A>) {
        val parameter = node as? SpecNode.Obj<A> ?: return
        if (parameter.isRef()) return
        if (parameter.str("in") == "body") {
            parameter["schema"]?.let(::walkSchema)
            return
        }
        // A non-body parameter IS its own (restricted) schema: type, items,
        // enum, minimum, ... sit directly on it. Its `x-example` is left
        // alone: a vendor extension some tools fill in as the raw,
        // serialized field text ("1,2,3" for an array), not as data.
        walkSimpleSchema(parameter)
    }

    private fun walkResponse2(node: SpecNode<A>) {
        val response = node as? SpecNode.Obj<A> ?: return
        if (response.isRef()) return
        val schema = response["schema"]
        if (schema != null) {
            walkSchema(schema)
            response.obj("examples")?.properties?.forEach { (mime, value) ->
                if (isJsonMediaType(mime)) add("examples.$mime", value, schema)
            }
        }
        response.obj("headers")?.properties?.values?.forEach(::walkSimpleSchema)
    }

    /** Swagger 2.0 non-body parameters, headers and their `items`. */
    private fun walkSimpleSchema(node: SpecNode<A>) {
        val schema = node as? SpecNode.Obj<A> ?: return
        schema["default"]?.let { add("default", it, schema) }
        schema["items"]?.let(::walkSimpleSchema)
    }
}
