package dev.gaphunter.openapicompanion.reference

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import dev.gaphunter.openapicompanion.licensing.CheckLicense
import dev.gaphunter.openapicompanion.pointer.JsonPointer
import java.net.URLDecoder

/**
 * Resolves a `$ref` value inside a JSON-formatted OpenAPI/Swagger spec
 * (`"#/components/schemas/Pet"`, `"pet.json"`,
 * `"./schemas/pet.json#/components/schemas/Pet"`) purely against local
 * files -- [resolveLocalFile] only ever walks
 * [VfsUtilCore.findRelativeFile] relative to the referencing file's own
 * directory. There is no HTTP client anywhere in this plugin; an
 * `http(s)://` $ref simply fails to resolve, shown as unresolved, the
 * same as any other broken reference -- never a network fetch. This is
 * the direct fix for the cited competitor complaint: a paid alternative
 * in this exact space doesn't support external references at all
 * ("not usable").
 *
 * Cross-format resolution (a JSON file's `$ref` pointing into a YAML
 * file, or vice versa) is a documented v1 scope cut -- same-format only
 * for now, see README.
 *
 * [isLicensed] is injectable (defaults to the real [CheckLicense] check)
 * so tests can exercise real resolution logic without a live
 * [com.intellij.ui.LicensingFacade], which is never initialized in a
 * test sandbox -- the same dependency-injection shape already used
 * elsewhere in this catalog (e.g. FirestoreRestClient's injectable
 * httpGet/httpPost) for the same reason.
 */
class JsonOpenApiRefReference(
    element: JsonStringLiteral,
    private val isLicensed: () -> Boolean = { CheckLicense.isLicensed() == true },
) : PsiReferenceBase<JsonStringLiteral>(element, ElementManipulators.getValueTextRange(element)) {

    override fun resolve(): PsiElement? {
        // 100% Paid, no free tier: navigation itself is the product, so it
        // never works without a license -- unlike this catalog's Freemium
        // plugins, where the base plugin stays fully functional and only a
        // specific extra feature gates on isLicensed().
        if (!isLicensed()) return null

        val refText = element.value
        val hashIndex = refText.indexOf('#')
        val filePart = if (hashIndex >= 0) refText.substring(0, hashIndex) else refText
        val pointerPart = if (hashIndex >= 0) refText.substring(hashIndex + 1) else ""

        val targetFile: JsonFile = if (filePart.isBlank()) {
            element.containingFile as? JsonFile ?: return null
        } else {
            resolveLocalFile(filePart) ?: return null
        }

        val decodedPointer = try {
            URLDecoder.decode(pointerPart, "UTF-8")
        } catch (e: Exception) {
            pointerPart
        }
        if (decodedPointer.isEmpty()) return targetFile.topLevelValue ?: targetFile

        val root = targetFile.topLevelValue ?: return null
        return JsonPointer.resolve(root, decodedPointer)
    }

    private fun resolveLocalFile(relativePath: String): JsonFile? {
        val currentVirtualFile = element.containingFile?.originalFile?.virtualFile ?: return null
        val baseDir = currentVirtualFile.parent ?: return null
        val targetVirtualFile = VfsUtilCore.findRelativeFile(relativePath, baseDir) ?: return null
        return PsiManager.getInstance(element.project).findFile(targetVirtualFile) as? JsonFile
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
