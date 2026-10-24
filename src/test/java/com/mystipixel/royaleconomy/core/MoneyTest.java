package com.mystipixel.royaleconomy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    // --- parse ---

    @Test
    void parseValidTwoDigits() {
        assertEquals(123450L, Money.parse("1234.50", 2));
        assertEquals(5L, Money.parse("0.05", 2));
        assertEquals(100L, Money.parse("1.00", 2));
        assertEquals(0L, Money.parse("0.00", 2));
        assertEquals(999999L, Money.parse("9999.99", 2));
    }

    @Test
    void parsePadsShortFraction() {
        assertEquals(123450L, Money.parse("1234.5", 2));
        assertEquals(500L, Money.parse("5", 2));
        assertEquals(500L, Money.parse("5.", 2));
    }

    @Test
    void parseZeroDigits() {
        assertEquals(1234L, Money.parse("1234", 0));
        assertEquals(0L, Money.parse("0", 0));
        assertEquals(999L, Money.parse("999", 0));
    }

    @Test
    void parseFourDigits() {
        assertEquals(12345678L, Money.parse("1234.5678", 4));
        assertEquals(10000L, Money.parse("1", 4));
    }

    @Test
    void parseRejectsNegative() {
        assertThrows(NumberFormatException.class, () -> Money.parse("-5", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse("-0.01", 2));
    }

    @Test
    void parseRejectsTooPrecise() {
        assertThrows(NumberFormatException.class, () -> Money.parse("1.234", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse("1.12345", 4));
    }

    @Test
    void parseRejectsNonNumeric() {
        assertThrows(NumberFormatException.class, () -> Money.parse("abc", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse("1.2x", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse("1e5", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse("1E5", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse(" 12", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse("1 2", 2));
    }

    @Test
    void parseRejectsNanAndInfinity() {
        assertThrows(NumberFormatException.class, () -> Money.parse("NaN", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse("nan", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse("Infinity", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse("-Infinity", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse("infinity", 2));
    }

    @Test
    void parseRejectsHugeValue() {
        assertThrows(NumberFormatException.class, () -> Money.parse("99999999999999999999", 0));
        assertThrows(NumberFormatException.class, () -> Money.parse("999999999999999999.99", 2));
    }

    @Test
    void parseRejectsEmpty() {
        assertThrows(NumberFormatException.class, () -> Money.parse("", 2));
        assertThrows(NumberFormatException.class, () -> Money.parse(null, 2));
    }

    @Test
    void parseRejectsMultipleDots() {
        assertThrows(NumberFormatException.class, () -> Money.parse("1.2.3", 2));
    }

    // --- fromDouble ---

    @Test
    void fromDoubleBasic() {
        assertEquals(123450L, Money.fromDouble(1234.50, 2));
        assertEquals(0L, Money.fromDouble(0.0, 2));
        assertEquals(5L, Money.fromDouble(0.05, 2));
        assertEquals(100L, Money.fromDouble(1.0, 2));
    }

    @Test
    void fromDoubleRoundsHalfUp() {
        assertEquals(1L, Money.fromDouble(0.005, 2));
        assertEquals(2L, Money.fromDouble(0.015, 2));
        assertEquals(0L, Money.fromDouble(0.004, 2));
        assertEquals(101L, Money.fromDouble(1.005, 2));
    }

    @Test
    void fromDoubleNegativeHalfUp() {
        assertEquals(0L, Money.fromDouble(-0.5, 0));
        assertEquals(-1L, Money.fromDouble(-1.5, 0));
        assertEquals(0L, Money.fromDouble(-0.005, 2));
    }

    @Test
    void fromDoubleRejectsNan() {
        assertThrows(NumberFormatException.class, () -> Money.fromDouble(Double.NaN, 2));
    }

    @Test
    void fromDoubleRejectsInfinity() {
        assertThrows(NumberFormatException.class, () -> Money.fromDouble(Double.POSITIVE_INFINITY, 2));
        assertThrows(NumberFormatException.class, () -> Money.fromDouble(Double.NEGATIVE_INFINITY, 2));
    }

    @Test
    void fromDoubleRejectsOverflow() {
        assertThrows(NumberFormatException.class, () -> Money.fromDouble(1e20, 2));
        assertThrows(NumberFormatException.class, () -> Money.fromDouble(Double.MAX_VALUE, 0));
    }

    // --- toDouble ---

    @Test
    void toDoubleBasic() {
        assertEquals(1234.50, Money.toDouble(123450L, 2), 1e-9);
        assertEquals(0.0, Money.toDouble(0L, 2), 1e-9);
        assertEquals(5.0, Money.toDouble(500L, 2), 1e-9);
        assertEquals(1234.5678, Money.toDouble(12345678L, 4), 1e-9);
        assertEquals(1234.0, Money.toDouble(1234L, 0), 1e-9);
    }

    // --- plain ---

    @Test
    void plainTwoDigits() {
        assertEquals("1,234.50", Money.plain(123450L, 2));
        assertEquals("0.00", Money.plain(0L, 2));
        assertEquals("1.00", Money.plain(100L, 2));
        assertEquals("9,999.99", Money.plain(999999L, 2));
        assertEquals("1,234,567.89", Money.plain(123456789L, 2));
    }

    @Test
    void plainZeroDigits() {
        assertEquals("123,450", Money.plain(123450L, 0));
        assertEquals("0", Money.plain(0L, 0));
        assertEquals("1,234,567", Money.plain(1234567L, 0));
    }

    @Test
    void plainFourDigits() {
        assertEquals("1,234.5678", Money.plain(12345678L, 4));
        assertEquals("0.0001", Money.plain(1L, 4));
        assertEquals("1.0000", Money.plain(10000L, 4));
    }

    @Test
    void plainNegative() {
        assertEquals("-1,234.50", Money.plain(-123450L, 2));
        assertEquals("-0.05", Money.plain(-5L, 2));
    }

    // --- round trip ---

    @Test
    void roundTripZeroDigits() {
        long[] values = {0L, 1L, 999L, 12345L, 999999L, 123456789L};
        for (long v : values) {
            double d = Money.toDouble(v, 0);
            long back = Money.fromDouble(d, 0);
            assertEquals(v, back, "round trip 0 digits failed for " + v);
        }
    }

    @Test
    void roundTripTwoDigits() {
        long[] values = {0L, 1L, 5L, 100L, 123450L, 999999L, 123456789L};
        for (long v : values) {
            double d = Money.toDouble(v, 2);
            long back = Money.fromDouble(d, 2);
            assertEquals(v, back, "round trip 2 digits failed for " + v);
        }
    }

    @Test
    void roundTripFourDigits() {
        long[] values = {0L, 1L, 10000L, 12345678L, 99999999L};
        for (long v : values) {
            double d = Money.toDouble(v, 4);
            long back = Money.fromDouble(d, 4);
            assertEquals(v, back, "round trip 4 digits failed for " + v);
        }
    }

    @Test
    void roundTripParseAndPlain() {
        String[] inputs = {"0.00", "1.50", "1234.56", "9999.99", "12345.67"};
        for (String input : inputs) {
            long minor = Money.parse(input, 2);
            String formatted = Money.plain(minor, 2);
            String stripped = formatted.replace(",", "");
            assertEquals(input, stripped, "plain(parse) round trip failed for " + input);
        }
    }
}
