package dev.gaphunter.openapicompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Precision run on a REAL corpus of public OpenAPI/Swagger specs, through the real highlighting pipeline
 * (both example-check inspections, licensed). Skipped unless `-Popenapi.corpus=<dir>` is given, so a normal
 * `./gradlew test` never touches it; the corpus lives outside the repo and is never committed.
 *
 * Writes a tab-separated report `file, line, message` (`-Popenapi.corpus.report=<file>`): every row is a
 * finding to review by hand against the spec's own text -- a real mistake in the spec is a true positive,
 * anything the spec author would call valid is a false positive to fix before release.
 */
class OpenApiCorpusPrecisionTest : BasePlatformTestCase() {

    fun testCorpus() {
        val root = System.getProperty("openapi.corpus")?.let(::File) ?: return
        val reportFile = File(System.getProperty("openapi.corpus.report") ?: "openapi-corpus-report.tsv")
        val files = ArrayList<File>()
        java.nio.file.Files.walk(root.toPath()).use { stream ->
            stream.forEach { if (java.nio.file.Files.isRegularFile(it) && it.toFile().length() < 1_500_000L) files.add(it.toFile()) }
        }
        files.sortBy { it.path }
        myFixture.enableInspections(OpenApiYamlExampleSchemaInspection { true }, OpenApiJsonExampleSchemaInspection { true })

        val report = StringBuilder()
        var scanned = 0
        var skipped = 0
        var withFindings = 0
        var total = 0
        for (file in files) {
            val relative = file.relativeTo(root).path.replace('\\', '/')
            try {
                myFixture.configureByText(file.name, file.readText())
                val document = myFixture.editor.document
                val hits = myFixture.doHighlighting().filter { it.description?.contains("doesn't match its schema") == true }
                scanned++
                if (hits.isNotEmpty()) withFindings++
                for (info in hits) {
                    total++
                    report.append(relative).append('\t').append(document.getLineNumber(info.startOffset) + 1).append('\t')
                        .append(info.description!!.take(200)).append('\n')
                }
            } catch (e: Throwable) {
                // An IDE-internal failure on one file (the test classpath's Kotlin stdlib is older than the IDE's) is skipped.
                skipped++
            }
        }
        report.append("# files=").append(files.size).append(" scanned=").append(scanned).append(" skipped=").append(skipped)
            .append(" files_with_findings=").append(withFindings).append(" findings=").append(total).append('\n')
        reportFile.writeText(report.toString())
    }
}
