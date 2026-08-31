package dev.gaphunter.openapicompanion.detection

/**
 * Flags the specific, real, common mistake of leaving a `$ref` written
 * for the *other* OAS version's schema-location convention: Swagger 2.0
 * puts reusable schemas under `#/definitions/...`, while OpenAPI 3.x
 * moved them to `#/components/schemas/...`. A spec migrated from one to
 * the other (or a `$ref` copy-pasted from old documentation) silently
 * fails to resolve with no clue why -- this gives the specific, helpful
 * reason instead of the generic "cannot resolve reference" message,
 * without doing any broader OAS-version-aware validation (see README
 * "v1 scope cuts" -- this is a narrow, real slice of that larger gap,
 * not the whole thing).
 */
object RefConventionMismatch {

    private const val SWAGGER_2_PREFIX = "/definitions/"
    private const val OPENAPI_3_PREFIX = "/components/schemas/"

    /**
     * [refText] is the raw `$ref` value as written (e.g.
     * `"#/definitions/Pet"` or `"./other.yaml#/components/schemas/Pet"`).
     * Returns a specific explanation when the fragment uses the other
     * version's convention, or null when there's nothing to say (either
     * it matches the current document's own convention, or it's some
     * other shape entirely -- e.g. no fragment at all, or a fragment
     * that isn't the convention's reusable-schemas path).
     */
    fun describe(refText: String, specVersion: OpenApiDetector.SpecVersion): String? {
        val hashIndex = refText.indexOf('#')
        if (hashIndex < 0) return null
        val fragment = refText.substring(hashIndex + 1)

        return when (specVersion) {
            OpenApiDetector.SpecVersion.OPENAPI_3 ->
                if (fragment.startsWith(SWAGGER_2_PREFIX)) {
                    "uses the Swagger 2.0 '#/definitions/...' convention, but this is an OpenAPI 3.x document -- schemas live under '#/components/schemas/...' here"
                } else {
                    null
                }
            OpenApiDetector.SpecVersion.SWAGGER_2 ->
                if (fragment.startsWith(OPENAPI_3_PREFIX)) {
                    "uses the OpenAPI 3.x '#/components/schemas/...' convention, but this is a Swagger 2.0 document -- schemas live under '#/definitions/...' here"
                } else {
                    null
                }
        }
    }
}
