package com.cinema.modules.movie.util;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;

public record ExcelStyles(
        CellStyle header, CellStyle input, CellStyle data, CellStyle sample, Font headerFont, Font requiredFont) {
}
