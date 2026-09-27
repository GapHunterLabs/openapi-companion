package dev.gaphunter.openapicompanion.example

import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate

/**
 * The `format` keyword for the formats whose validity is unambiguous: the JSON Schema string
 * formats a spec author writes an example for (`date-time`, `date`, `time`, `email`, `uuid`,
 * `uri`, `ipv4`, `ipv6`, `hostname`, `byte`) and OpenAPI's integer widths (`int32`, `int64`).
 *
 * Fail-closed like the rest of [SchemaValidator]: a format this doesn't know (a custom one, or
 * `binary`, `password`, `float`, `double`, `uri-reference`, `iri`, ...) is never reported, and
 * each check is written to accept anything a strict validator would accept -- it may miss an
 * odd invalid value, it must never reject a valid one. Returns the reason a value is wrong, or
 * null when it is fine (or unknowable).
 *
 * An empty string is never reported: it is the usual placeholder for "no value given" (found on
 * real specs -- `value: - ""` under a `uuid` schema), the string counterpart of a top-level null example.
 *
 * `email` deliberately needs only `local@domain` without spaces: full RFC 5322 is not something
 * to re-implement, and `user@localhost` is a valid address.
 */
object FormatChecker {

    private val DATE = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val TIME = Regex("""(\d{2}):(\d{2}):(\d{2})(\.\d+)?([Zz]|[+-]\d{2}(:?\d{2})?)?""")
    private val EMAIL = Regex("""[^\s@]+@[^\s@]+""")
    private val UUID = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
    private val URI = Regex("""[A-Za-z][A-Za-z0-9+.\-]*:\S*""")
    private val IPV4 = Regex("""((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)""")
    private val HEX_GROUP = Regex("""[0-9a-fA-F]{1,4}""")
    private val HOSTNAME_LABEL = Regex("""[A-Za-z0-9]([A-Za-z0-9\-]{0,61}[A-Za-z0-9])?""")
    private val BASE64 = Regex("""([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?""")

    private val INT32_MIN = BigDecimal("-2147483648")
    private val INT32_MAX = BigDecimal("2147483647")
    private val INT64_MIN = BigDecimal("-9223372036854775808")
    private val INT64_MAX = BigDecimal("9223372036854775807")

    /** Why [text] is not a valid [format] string, or null when it is valid or the format isn't one this checks. */
    fun stringProblem(format: String, text: String): String? = if (text.isEmpty()) null else when (format) {
        "date-time" -> if (isDateTime(text)) null else "not a valid date-time (RFC 3339, e.g. 2024-05-17T09:30:00Z)"
        "date" -> if (isDate(text)) null else "not a valid date (YYYY-MM-DD)"
        "time" -> if (isTime(text)) null else "not a valid time (hh:mm:ss, optionally with an offset)"
        "email" -> if (EMAIL.matches(text)) null else "not a valid email address"
        "uuid" -> if (UUID.matches(text)) null else "not a valid UUID (8-4-4-4-12 hexadecimal digits)"
        "uri" -> if (URI.matches(text)) null else "not a valid URI (needs a scheme such as https:, and no spaces)"
        "ipv4" -> if (IPV4.matches(text)) null else "not a valid IPv4 address"
        "ipv6" -> if (isIpv6(text)) null else "not a valid IPv6 address"
        "hostname" -> if (isHostname(text)) null else "not a valid hostname"
        "byte" -> if (BASE64.matches(text)) null else "not valid base64"
        else -> null
    }

    /** Why the integer [value] is outside the range of [format] (`int32`, `int64`), or null. */
    fun integerProblem(format: String, value: BigDecimal): String? = when (format) {
        "int32" -> if (value < INT32_MIN || value > INT32_MAX) "outside the range of int32 (-2147483648 to 2147483647)" else null
        "int64" -> if (value < INT64_MIN || value > INT64_MAX) "outside the range of int64" else null
        else -> null
    }

    private fun isDate(text: String): Boolean {
        val match = DATE.matchEntire(text) ?: return false
        return try {
            LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
            true
        } catch (e: DateTimeException) {
            false
        }
    }

    private fun isTime(text: String): Boolean {
        val match = TIME.matchEntire(text) ?: return false
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues[3].toInt()
        return hour <= 23 && minute <= 59 && second <= 60 // 60: a leap second, valid in RFC 3339
    }

    /** RFC 3339: a date, `T` (or a space, which RFC 3339 allows and many specs use), a time WITH an offset. */
    private fun isDateTime(text: String): Boolean {
        if (text.length < 11) return false
        val separator = text[10]
        if (separator != 'T' && separator != 't' && separator != ' ') return false
        val time = text.substring(11)
        return isDate(text.substring(0, 10)) && isTime(time) && TIME.matchEntire(time)!!.groupValues[5].isNotEmpty()
    }

    private fun isHostname(text: String): Boolean {
        val name = text.removeSuffix(".")
        if (name.isEmpty() || name.length > 253) return false
        return name.split('.').all { HOSTNAME_LABEL.matches(it) }
    }

    private fun isIpv6(text: String): Boolean {
        if (!text.contains(':') || text.count { it == ':' } > 8 || ":::" in text) return false
        val halves = text.split("::")
        if (halves.size > 2) return false
        fun groups(part: String): List<String>? = if (part.isEmpty()) emptyList() else part.split(':')
        val head = groups(halves[0]) ?: return false
        val tail = if (halves.size == 2) groups(halves[1]) ?: return false else emptyList()
        val all = head + tail
        // An embedded IPv4 tail (`::ffff:192.168.0.1`) counts as two groups.
        val last = all.lastOrNull()
        val embeddedIpv4 = last != null && last.contains('.')
        if (embeddedIpv4 && !IPV4.matches(last!!)) return false
        val hexGroups = if (embeddedIpv4) all.dropLast(1) else all
        if (!hexGroups.all { HEX_GROUP.matches(it) }) return false
        val count = hexGroups.size + if (embeddedIpv4) 2 else 0
        return if (halves.size == 2) count < 8 else count == 8
    }
}
