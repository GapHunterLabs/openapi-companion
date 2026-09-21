package dev.gaphunter.openapicompanion.usage

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext

/**
 * Answers "is this pointer written anywhere in the project?". Deliberately
 * textual rather than going through this plugin's own `$ref` resolver: that
 * resolver only runs inside files `OpenApiDetector` recognizes and only
 * follows same-format references, while real multi-file specs point at
 * components from fragment files with no top-level `openapi:` key and from
 * files in the other format. The word index narrows the search to
 * candidate files first (`WordIndexCoverageTest` pins down that it covers
 * text inside YAML/JSON strings), so this never reads every file.
 *
 * One instance per inspection pass: file texts are cached for that pass
 * only, so a spec with hundreds of components reads each candidate once.
 */
class ProjectRefTextSearch(private val project: Project) {
    private val scope = GlobalSearchScope.projectScope(project)
    private val psiManager = PsiManager.getInstance(project)
    private val texts = HashMap<VirtualFile, CharSequence?>()

    fun isReferencedAnywhere(pointer: String): Boolean =
        ComponentUsageMatcher.needles(pointer).any { needle -> anyCandidateContains(needle) }

    private fun anyCandidateContains(needle: String): Boolean {
        var found = false
        PsiSearchHelper.getInstance(project).processCandidateFilesForText(scope, UsageSearchContext.ANY, true, needle) { file ->
            ProgressManager.checkCanceled()
            val text = texts.getOrPut(file) { psiManager.findFile(file)?.viewProvider?.contents }
            found = text != null && ComponentUsageMatcher.containsPointer(text, needle)
            !found
        }
        return found
    }
}
