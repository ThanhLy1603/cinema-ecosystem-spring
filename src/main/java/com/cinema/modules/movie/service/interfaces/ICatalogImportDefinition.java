package com.cinema.modules.movie.service.interfaces;

import com.cinema.modules.movie.enums.CatalogImportType;
import com.cinema.modules.movie.model.CatalogImportLayout;
import com.cinema.modules.movie.model.CatalogImportRow;

import java.util.List;

public interface ICatalogImportDefinition {

    CatalogImportType type();

    CatalogImportLayout layout();

    List<String> existingNames();

    List<String> validate(CatalogImportRow catalogImportRow);

    String describe(CatalogImportRow catalogImportRow);

    void create(CatalogImportRow catalogImportRow);
}
