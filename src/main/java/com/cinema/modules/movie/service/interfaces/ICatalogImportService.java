package com.cinema.modules.movie.service.interfaces;

import com.cinema.modules.movie.dto.response.CatalogImportReportResponse;
import com.cinema.modules.movie.enums.CatalogImportType;

import java.util.Collection;

public interface ICatalogImportService {

    byte[] buildTemplate(CatalogImportType catalogImportType);

    String templateFilename(CatalogImportType catalogImportType);

    CatalogImportReportResponse previewImport(CatalogImportType catalogImportType, byte[] content);

    CatalogImportReportResponse importRows(CatalogImportType catalogImportType, byte[] content);

    byte[] exportRows(CatalogImportType catalogImportType, byte[] content, Collection<Integer> rowNumbers);
}
