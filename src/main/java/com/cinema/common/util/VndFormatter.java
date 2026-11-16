package com.cinema.common.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class VndFormatter {

    private static final DecimalFormatSymbols VIETNAM_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.of("vi", "VN"));

    private VndFormatter() {
    }

    public static String format(BigDecimal amount) {
        return new DecimalFormat("#,##0", VIETNAM_SYMBOLS).format(amount) + "đ";
    }
}
