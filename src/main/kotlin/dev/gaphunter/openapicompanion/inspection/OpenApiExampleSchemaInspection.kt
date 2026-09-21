package dev.gaphunter.openapicompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.gaphunter.openapicompanion.detection.OpenApiDetector
import dev.gaphunter.openapicompanion.example.ExampleSiteFinder
import dev.gaphunter.openapicompanion.example.ProjectSchemaRefResolver
import dev.gaphunter.openapicompanion.example.PsiSpecNodes
import dev.gaphunter.openapicompanion.example.SchemaValidator
import dev.gaphunter.openapicompanion.example.SpecNode
import dev.gaphunter.openapicompanion.licensing.CheckLicense

/**
 * Flags an inline `example`/`default` value that doesn't match the schema
 * it illustrates -- the same check as Spectral's
 * `oas3-valid-schema-example`/`oas3-valid-media-example` (and their OAS 2
 * counterparts) and Redocly's `no-invalid-schema-examples`/
 * `no-invalid-media-type-examples`/`no-invalid-parameter-examples`, with no
 * CLI or account involved. Fail-closed by design, see [SchemaValidator].
 *
 * Same licensing and review-prompt rules as the unused-component check:
 * nothing without a license, never counted by `ReviewPrompt`.
 */
abstract class OpenApiExampleSchemaInspectionBase(
    private val isLicensed: () -> Boolean,
) : LocalInspectionTool() {

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        val version = OpenApiDetector.detect(file.text) ?: return null
        if (!isLicensed()) return null
        val root = PsiSpecNodes.of(file) ?: return null
        val checkCanceled = { ProgressManager.checkCanceled() }
        val sites = ExampleSiteFinder.find(root, version, checkCanceled)
        if (sites.isEmpty()) return null

        val resolver = ProjectSchemaRefResolver(file, root)
        val validator = SchemaValidator(
            ExampleSiteFinder.usesJsonSchemaSemantics(root, version),
            resolver::resolve,
            checkCanceled,
        )
        return sites.flatMap { site ->
            validator.validate(site.value, site.schema).map { violation ->
                manager.createProblemDescriptor(
                    highlightOf(violation.at),
                    SchemaValidator.describe(site.label, violation),
                    isOnTheFly,
                    LocalQuickFix.EMPTY_ARRAY,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }.toTypedArray()
    }

    /** A scalar is highlighted itself; an object or array by the key it
     * sits under, rather than its whole multi-line body. */
    private fun highlightOf(node: SpecNode<PsiElement>): PsiElement = when (node) {
        is SpecNode.Obj, is SpecNode.Arr -> node.keyAnchor ?: node.anchor
        else -> node.anchor
    }
}

// Each class name minus "Inspection" is the platform's default shortName,
// which has to match the shortName registered in plugin.xml.
class OpenApiYamlExampleSchemaInspection(
    isLicensed: () -> Boolean = { CheckLicense.isLicensed() == true },
) : OpenApiExampleSchemaInspectionBase(isLicensed)

class OpenApiJsonExampleSchemaInspection(
    isLicensed: () -> Boolean = { CheckLicense.isLicensed() == true },
) : OpenApiExampleSchemaInspectionBase(isLicensed)
