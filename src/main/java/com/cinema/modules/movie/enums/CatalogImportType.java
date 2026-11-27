package com.cinema.modules.movie.enums;

import java.util.Locale;

public enum CatalogImportType {
    GENRES,
    PERSONS;

    public static CatalogImportType fromPath(String path) {
        return valueOf(path.toUpperCase(Locale.ROOT));
    }
}
