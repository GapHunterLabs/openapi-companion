package dev.gaphunter.openapicompanion.example

import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonBooleanLiteral
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonNullLiteral
import com.intellij.json.psi.JsonNumberLiteral
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.json.psi.JsonValue
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.gaphunter.openapicompanion.example.YamlScalarTyping.Kind
import org.jetbrains.yaml.psi.YAMLBlockScalar
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLQuotedText
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLValue
import java.math.BigDecimal

/**
 * The only place the example check reads spec PSI: turns a YAML or JSON
 * file into a [SpecNode] tree. Everything after this is format-agnostic,
 * which is also what lets a YAML schema `$ref` a JSON file and vice versa.
 */
object PsiSpecNodes {

    fun of(file: PsiFile): SpecNode<PsiElement>? = when (file) {
        is YAMLFile -> file.documents.firstOrNull()?.topLevelValue?.let { yaml(it, null) }
        is JsonFile -> file.topLevelValue?.let { json(it, null) }
        else -> null
    }

    private fun yaml(value: YAMLValue, key: PsiElement?): SpecNode<PsiElement> {
        ProgressManager.checkCanceled()
        // An explicit tag (!!str, !!int, a custom one) overrides the usual
        // typing in ways not worth second-guessing.
        if (value.tag != null) return SpecNode.Unknown(null, value, key)
        return when (value) {
            is YAMLMapping -> {
                // A merge key pulls in properties from elsewhere -- the
                // mapping alone doesn't show the real set of keys.
                if (value.keyValues.any { it.keyText == "<<" }) return SpecNode.Unknown(null, value, key)
                val properties = LinkedHashMap<String, SpecNode<PsiElement>>()
                for (keyValue in value.keyValues) {
                    val keyElement = keyValue.key ?: keyValue
                    val child = keyValue.value
                    properties.putIfAbsent(
                        keyValue.keyText,
                        if (child == null) SpecNode.Null(keyValue, keyElement) else yaml(child, keyElement),
                    )
                }
                SpecNode.Obj(properties, value, key)
            }
            is YAMLSequence -> SpecNode.Arr(
                value.items.map { item -> item.value?.let { yaml(it, null) } ?: SpecNode.Null(item, null) },
                value,
                key,
            )
            is YAMLQuotedText, is YAMLBlockScalar -> SpecNode.Str((value as YAMLScalar).textValue, value, key)
            is YAMLScalar -> when (val kind = YamlScalarTyping.classifyPlain(value.textValue)) {
                Kind.NullValue -> SpecNode.Null(value, key)
                is Kind.BoolValue -> SpecNode.Bool(kind.value, value, key)
                is Kind.NumberValue -> SpecNode.Num(kind.value, value, key)
                is Kind.StringValue -> SpecNode.Str(kind.value, value, key)
                Kind.Ambiguous -> SpecNode.Unknown(value.textValue, value, key)
            }
            // Aliases and anything else.
            else -> SpecNode.Unknown(null, value, key)
        }
    }

    private fun json(value: JsonValue, key: PsiElement?): SpecNode<PsiElement> {
        ProgressManager.checkCanceled()
        return when (value) {
            is JsonObject -> {
                val properties = LinkedHashMap<String, SpecNode<PsiElement>>()
                for (property in value.propertyList) {
                    val child = property.value
                    properties.putIfAbsent(
                        property.name,
                        if (child == null) SpecNode.Unknown(null, property, property.nameElement) else json(child, property.nameElement),
                    )
                }
                SpecNode.Obj(properties, value, key)
            }
            is JsonArray -> SpecNode.Arr(value.valueList.map { json(it, null) }, value, key)
            is JsonStringLiteral -> SpecNode.Str(value.value, value, key)
            is JsonNumberLiteral -> value.text.toBigDecimalOrNull()?.let { SpecNode.Num(it, value, key) }
                ?: SpecNode.Unknown(value.text, value, key)
            is JsonBooleanLiteral -> SpecNode.Bool(value.value, value, key)
            is JsonNullLiteral -> SpecNode.Null(value, key)
            // Unquoted identifiers the lenient JSON parser accepts, etc.
            else -> SpecNode.Unknown(null, value, key)
        }
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = try {
        BigDecimal(this)
    } catch (e: NumberFormatException) {
        null
    }
}
