package dev.gaphunter.openapicompanion.reference

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import dev.gaphunter.openapicompanion.licensing.CheckLicense
import dev.gaphunter.openapicompanion.pointer.YamlPointer
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLScalar
import java.net.URLDecoder

/**
 * YAML counterpart of [JsonOpenApiRefReference] -- same resolution
 * rules (local files only, RFC 6901 pointer via [YamlPointer]), applied
 * to a `$ref` scalar value inside a YAML-formatted OpenAPI/Swagger spec.
 * `getTextValue()` already gives the decoded scalar content regardless
 * of quoting style (plain, single-, or double-quoted), so there's no
 * separate unquoting step needed the way [JsonStringLiteral.value]
 * already handles it for JSON.
 *
 * [isLicensed] is injectable for the same reason as
 * [JsonOpenApiRefReference]'s -- see its own doc comment.
 */
class YamlOpenApiRefReference(
    element: YAMLScalar,
    private val isLicensed: () -> Boolean = { CheckLicense.isLicensed() == true },
) : PsiReferenceBase<YAMLScalar>(element, ElementManipulators.getValueTextRange(element)) {

    override fun resolve(): PsiElement? {
        // 100% Paid, no free tier -- see JsonOpenApiRefReference's own comment.
        if (!isLicensed()) return null

        val refText = element.textValue
        val hashIndex = refText.indexOf('#')
        val filePart = if (hashIndex >= 0) refText.substring(0, hashIndex) else refText
        val pointerPart = if (hashIndex >= 0) refText.substring(hashIndex + 1) else ""

        val targetFile: YAMLFile = if (filePart.isBlank()) {
            element.containingFile as? YAMLFile ?: return null
        } else {
            resolveLocalFile(filePart) ?: return null
        }

        val decodedPointer = try {
            URLDecoder.decode(pointerPart, "UTF-8")
        } catch (e: Exception) {
            pointerPart
        }
        val root = targetFile.documents.firstOrNull()?.topLevelValue
        if (decodedPointer.isEmpty()) return root ?: targetFile
        if (root == null) return null

        return YamlPointer.resolve(root, decodedPointer)
    }

    private fun resolveLocalFile(relativePath: String): YAMLFile? {
        val currentVirtualFile = element.containingFile?.originalFile?.virtualFile ?: return null
        val baseDir = currentVirtualFile.parent ?: return null
        val targetVirtualFile = VfsUtilCore.findRelativeFile(relativePath, baseDir) ?: return null
        return PsiManager.getInstance(element.project).findFile(targetVirtualFile) as? YAMLFile
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
