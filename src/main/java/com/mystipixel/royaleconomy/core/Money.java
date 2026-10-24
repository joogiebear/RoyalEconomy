package com.mystipixel.royaleconomy.core;

import java.math.BigDecimal;
import java.util.Locale;

public final class Money {

    private Money() {
    }

    public static long parse(String text, int digits) {
        if (text == null || text.isEmpty()) {
            throw new NumberFormatException("empty input");
        }

        String s = text;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.equals("nan") || lower.equals("infinity") || lower.equals("-infinity") || lower.equals("+infinity")) {
            throw new NumberFormatException("not a number: " + text);
        }

        if (s.startsWith("-")) {
            throw new NumberFormatException("negative: " + text);
        }
        if (s.startsWith("+")) {
            s = s.substring(1);
        }
        if (s.contains("e") || s.contains("E")) {
            throw new NumberFormatException("scientific notation: " + text);
        }

        String[] parts = s.split("\\.", -1);
        if (parts.length > 2) {
            throw new NumberFormatException("multiple decimal points: " + text);
        }

        String intPart = parts[0];
        String fracPart = parts.length == 2 ? parts[1] : "";

        if (!intPart.isEmpty() && !intPart.matches("[0-9]+")) {
            throw new NumberFormatException("non-numeric integer part: " + text);
        }
        if (!fracPart.isEmpty() && !fracPart.matches("[0-9]+")) {
            throw new NumberFormatException("non-numeric fractional part: " + text);
        }
        if (fracPart.length() > digits) {
            throw new NumberFormatException("too many fractional digits: " + text);
        }

        long intVal = intPart.isEmpty() ? 0 : Long.parseLong(intPart);
        long fracVal = fracPart.isEmpty() ? 0 : Long.parseLong(fracPart);

        for (int i = fracPart.length(); i < digits; i++) {
            fracVal *= 10;
        }

        int multiplier = 1;
        for (int i = 0; i < digits; i++) {
            multiplier *= 10;
        }

        if (intVal > Long.MAX_VALUE / multiplier) {
            throw new NumberFormatException("overflow: " + text);
        }
        long result = intVal * multiplier;
        if (fracVal > Long.MAX_VALUE - result) {
            throw new NumberFormatException("overflow: " + text);
        }
        result += fracVal;
        return result;
    }

    public static long fromDouble(double value, int digits) {
        if (Double.isNaN(value)) {
            throw new NumberFormatException("NaN");
        }
        if (Double.isInfinite(value)) {
            throw new NumberFormatException("infinite");
        }

        int multiplier = 1;
        for (int i = 0; i < digits; i++) {
            multiplier *= 10;
        }

        BigDecimal bd = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(multiplier));
        if (bd.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                || bd.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0) {
            throw new NumberFormatException("overflow: " + value);
        }
        return Math.round(bd.doubleValue());
    }

    public static double toDouble(long minorUnits, int digits) {
        int multiplier = 1;
        for (int i = 0; i < digits; i++) {
            multiplier *= 10;
        }
        return minorUnits / (double) multiplier;
    }

    public static String plain(long minorUnits, int digits) {
        int multiplier = 1;
        for (int i = 0; i < digits; i++) {
            multiplier *= 10;
        }

        boolean negative = minorUnits < 0;
        long abs = negative ? -minorUnits : minorUnits;
        long whole = abs / multiplier;
        long frac = abs % multiplier;

        StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append('-');
        }

        String wholeStr = Long.toString(whole);
        for (int i = 0; i < wholeStr.length(); i++) {
            if (i > 0 && (wholeStr.length() - i) % 3 == 0) {
                sb.append(',');
            }
            sb.append(wholeStr.charAt(i));
        }

        if (digits > 0) {
            sb.append('.');
            String fracStr = Long.toString(frac);
            for (int i = fracStr.length(); i < digits; i++) {
                sb.append('0');
            }
            sb.append(fracStr);
        }

        return sb.toString();
    }
}
