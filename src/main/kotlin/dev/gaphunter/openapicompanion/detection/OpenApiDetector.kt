package dev.gaphunter.openapicompanion.detection

/**
 * Detects an OpenAPI/Swagger spec by real content, never by file
 * extension -- a `.json`/`.yaml`/`.yml` file is meaningless on its own,
 * the same principle already established across this catalog
 * (`json-schema-companion`'s `$schema` check, `ansible-companion`'s
 * content heuristic). A real spec always declares its version at the
 * top level: `openapi: "3.x.x"` (OAS 3.x) or `swagger: "2.0"` (OAS 2.0
 * / Swagger). Pure text scan -- callers pass in the already-loaded file
 * text, no I/O here.
 */
object OpenApiDetector {
    // YAML's top-level keys are conventionally unindented at column 0, so a
    // line-start anchor is a real, format-specific signal there. JSON has no
    // such convention (a compact, single-line document is completely valid),
    // so its check only requires the exact quoted key with nothing between
    // the quotes -- separate patterns per format, not one shared regex
    // trying to approximate both.
    private val YAML_OPENAPI_3 = Regex("""(?m)^openapi:\s*["']?3\.""")
    private val YAML_SWAGGER_2 = Regex("""(?m)^swagger:\s*["']?2\.0""")
    private val JSON_OPENAPI_3 = Regex(""""openapi"\s*:\s*"3\.""")
    private val JSON_SWAGGER_2 = Regex(""""swagger"\s*:\s*"2\.0"""")

    enum class SpecVersion { OPENAPI_3, SWAGGER_2 }

    /** Null means "not recognized as an OpenAPI/Swagger spec at all". */
    fun detect(text: String): SpecVersion? = when {
        YAML_OPENAPI_3.containsMatchIn(text) || JSON_OPENAPI_3.containsMatchIn(text) -> SpecVersion.OPENAPI_3
        YAML_SWAGGER_2.containsMatchIn(text) || JSON_SWAGGER_2.containsMatchIn(text) -> SpecVersion.SWAGGER_2
        else -> null
    }
}
