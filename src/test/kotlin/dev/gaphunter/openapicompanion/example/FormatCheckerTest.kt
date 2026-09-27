package dev.gaphunter.openapicompanion.example

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class FormatCheckerTest {

    private fun ok(format: String, vararg values: String) {
        for (value in values) assertNull("$format should accept '$value'", FormatChecker.stringProblem(format, value))
    }

    private fun bad(format: String, vararg values: String) {
        for (value in values) assertNotNull("$format should reject '$value'", FormatChecker.stringProblem(format, value))
    }

    @Test
    fun dateTime() {
        ok("date-time", "2024-05-17T09:30:00Z", "2024-05-17T09:30:00.123Z", "2024-05-17T09:30:00+02:00", "2024-05-17t09:30:00z",
            "2024-05-17 09:30:00Z", "2016-12-31T23:59:60Z", "2024-05-17T09:30:00-0530")
        bad("date-time", "2024-05-17T09:30:00", "2024-05-17", "2024-13-17T09:30:00Z", "2024-02-30T09:30:00Z", "2024-05-17T25:30:00Z",
            "2024-05-17T09:61:00Z", "yesterday", "2024-05-17X09:30:00Z")
    }

    @Test
    fun date() {
        ok("date", "2024-05-17", "2024-02-29", "0001-01-01")
        bad("date", "2023-02-29", "2024-5-17", "17/05/2024", "2024-05-17T09:30:00Z", "2024-00-10")
    }

    @Test
    fun time() {
        ok("time", "09:30:00", "09:30:00Z", "09:30:00.5+02:00", "23:59:60Z")
        bad("time", "9:30", "24:00:00", "09:60:00", "noon")
    }

    @Test
    fun email() {
        ok("email", "user@example.com", "first.last+tag@sub.example.org", "user@localhost")
        bad("email", "not-an-email", "@example.com", "user@", "two@@example.com", "with space@example.com")
    }

    @Test
    fun uuid() {
        ok("uuid", "123e4567-e89b-12d3-a456-426614174000", "123E4567-E89B-12D3-A456-426614174000")
        bad("uuid", "order-123", "123e4567e89b12d3a456426614174000", "123e4567-e89b-12d3-a456-42661417400", "123e4567-e89b-12d3-a456-42661417400g")
    }

    @Test
    fun uri() {
        ok("uri", "https://example.com/a?b=c#d", "mailto:someone@example.com", "urn:isbn:0451450523", "file:///tmp/x")
        bad("uri", "/docs", "example.com/docs", "not a uri", "://missing-scheme")
    }

    @Test
    fun ipv4() {
        ok("ipv4", "192.168.1.1", "0.0.0.0", "255.255.255.255")
        bad("ipv4", "192.168.1.256", "192.168.1", "192.168.01.1", "1.2.3.4.5", "a.b.c.d")
    }

    @Test
    fun ipv6() {
        ok("ipv6", "2001:db8::8a2e:370:7334", "::1", "::", "1::", "2001:0db8:85a3:0000:0000:8a2e:0370:7334", "::ffff:192.168.0.1", "fe80::1")
        bad("ipv6", "2001:db8::8a2e::7334", "12345::1", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7", "gggg::1", ":::", "192.168.0.1")
    }

    @Test
    fun hostname() {
        ok("hostname", "example.com", "sub.example.co.uk", "localhost", "a-b.example.com", "example.com.", "123.example.com")
        bad("hostname", "-bad.example.com", "bad-.example.com", "exa mple.com", "under_score.example.com", "a".repeat(64) + ".com")
    }

    @Test
    fun byte() {
        ok("byte", "SGVsbG8=", "SGVsbG8gd29ybGQ=", "", "QUJD")
        bad("byte", "SGVsbG8", "not base64!", "SGVs=bG8=")
    }

    @Test
    fun integerWidths() {
        assertNull(FormatChecker.integerProblem("int32", BigDecimal("2147483647")))
        assertNull(FormatChecker.integerProblem("int32", BigDecimal("-2147483648")))
        assertNotNull(FormatChecker.integerProblem("int32", BigDecimal("2147483648")))
        assertNotNull(FormatChecker.integerProblem("int32", BigDecimal("-2147483649")))
        assertNull(FormatChecker.integerProblem("int64", BigDecimal("9223372036854775807")))
        assertNotNull(FormatChecker.integerProblem("int64", BigDecimal("9223372036854775808")))
    }

    @Test
    fun anEmptyStringIsAPlaceholderNotAMismatch() {
        for (format in listOf("date-time", "date", "time", "email", "uuid", "uri", "ipv4", "ipv6", "hostname", "byte")) {
            assertNull(format, FormatChecker.stringProblem(format, ""))
        }
    }

    @Test
    fun unknownFormatsAreNeverReported() {
        for (format in listOf("order-reference", "binary", "password", "float", "double", "uri-reference", "iri", "regex", "json-pointer", "decimal")) {
            assertNull(format, FormatChecker.stringProblem(format, "anything at all"))
            assertNull(format, FormatChecker.integerProblem(format, BigDecimal("99999999999999999999")))
        }
    }
}
