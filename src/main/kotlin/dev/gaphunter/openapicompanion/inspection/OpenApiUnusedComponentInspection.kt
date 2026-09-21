package dev.gaphunter.openapicompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import dev.gaphunter.openapicompanion.detection.OpenApiDetector
import dev.gaphunter.openapicompanion.licensing.CheckLicense
import dev.gaphunter.openapicompanion.usage.ComponentCollector
import dev.gaphunter.openapicompanion.usage.ComponentUsageMatcher
import dev.gaphunter.openapicompanion.usage.ProjectRefTextSearch

/**
 * Flags reusable components (`components/schemas/...` and the other
 * `$ref`-able sections, or Swagger 2.0 `definitions`) that nothing in the
 * project points to -- the same check as Spectral's
 * `oas3-unused-component`/`oas2-unused-definition` and Redocly's
 * `no-unused-components`, with no CLI or account involved.
 *
 * "Potentially" unused, same wording as Spectral: a consumer in another
 * repository is invisible from here. Reports nothing when unlicensed --
 * `OpenApiRefAnnotator` already says a license is required on every
 * `$ref`, and a second notice per component would be noise. Never feeds
 * `ReviewPrompt`, whose counter is defined as broken-`$ref` findings only.
 */
abstract class OpenApiUnusedComponentInspectionBase(
    private val isLicensed: () -> Boolean,
) : LocalInspectionTool() {

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        val version = OpenApiDetector.detect(file.text) ?: return null
        if (!isLicensed()) return null
        val declarations = ComponentCollector.collect(file, version)
        if (declarations.isEmpty()) return null

        val usedByDiscriminator = ComponentUsageMatcher.usedByDiscriminator(declarations)
        val search = ProjectRefTextSearch(file.project)
        return declarations.mapNotNull { declaration ->
            ProgressManager.checkCanceled()
            val pointer = ComponentUsageMatcher.pointerOf(declaration.section, declaration.name)
            if (pointer in usedByDiscriminator || search.isReferencedAnywhere(pointer)) return@mapNotNull null
            manager.createProblemDescriptor(
                declaration.anchor,
                "Potentially unused component '${declaration.name}' -- no \$ref to $pointer found in this project",
                isOnTheFly,
                LocalQuickFix.EMPTY_ARRAY,
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            )
        }.toTypedArray()
    }
}

// Each class name minus "Inspection" is the platform's default shortName,
// which has to match the shortName registered in plugin.xml.
class OpenApiYamlUnusedComponentInspection(
    isLicensed: () -> Boolean = { CheckLicense.isLicensed() == true },
) : OpenApiUnusedComponentInspectionBase(isLicensed)

class OpenApiJsonUnusedComponentInspection(
    isLicensed: () -> Boolean = { CheckLicense.isLicensed() == true },
) : OpenApiUnusedComponentInspectionBase(isLicensed)
