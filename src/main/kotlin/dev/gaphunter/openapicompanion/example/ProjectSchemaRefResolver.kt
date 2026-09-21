package dev.gaphunter.openapicompanion.example

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * Resolves a schema `$ref` for [SchemaValidator] against local files only --
 * same rule as go-to-definition: an `http(s)://` ref is never fetched, it
 * just doesn't resolve (and the value is then skipped, not reported).
 * Relative paths are taken from the file that contains the `$ref`, so a
 * schema pulled in from `schemas/pet.yaml` resolves its own refs from
 * there. Each file is converted at most once per inspection pass.
 */
class ProjectSchemaRefResolver(currentFile: PsiFile, currentRoot: SpecNode<PsiElement>) {
    private val psiManager = PsiManager.getInstance(currentFile.project)
    private val roots = HashMap<PsiFile, SpecNode<PsiElement>?>().apply { put(currentFile, currentRoot) }

    fun resolve(ref: String, from: SpecNode<PsiElement>): SpecNode<PsiElement>? {
        val (filePart, pointer) = SpecPointer.splitRef(ref) ?: return null
        val fromFile = from.anchor.containingFile ?: return null
        val targetFile = if (filePart.isEmpty()) {
            fromFile
        } else {
            if ("://" in filePart) return null
            val baseDir = fromFile.originalFile.virtualFile?.parent ?: return null
            val target = VfsUtilCore.findRelativeFile(filePart, baseDir) ?: return null
            psiManager.findFile(target) ?: return null
        }
        val root = roots.getOrPut(targetFile) { PsiSpecNodes.of(targetFile) } ?: return null
        return SpecPointer.resolve(root, pointer)
    }
}
