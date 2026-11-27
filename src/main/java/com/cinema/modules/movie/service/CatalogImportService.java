package com.cinema.modules.movie.service;

import com.cinema.common.exception.AppException;
import com.cinema.modules.movie.dto.response.CatalogImportReportResponse;
import com.cinema.modules.movie.dto.response.CatalogImportRowResponse;
import com.cinema.modules.movie.enums.CatalogImportType;
import com.cinema.modules.movie.enums.ImportRowStatus;
import com.cinema.modules.movie.model.CatalogImportRow;
import com.cinema.modules.movie.service.interfaces.ICatalogImportDefinition;
import com.cinema.modules.movie.service.interfaces.ICatalogImportService;
import com.cinema.modules.movie.util.ExcelSheets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogImportService implements ICatalogImportService {

    private final CatalogImportWorkbook catalogImportWorkbook;
    private final List<ICatalogImportDefinition> catalogImportDefinitions;

    @Override
    public byte[] buildTemplate(CatalogImportType catalogImportType) {
        ICatalogImportDefinition catalogImportDefinition = definitionOf(catalogImportType);
        return catalogImportWorkbook.writeTemplate(
                catalogImportDefinition.layout(), catalogImportDefinition.existingNames());
    }

    @Override
    public String templateFilename(CatalogImportType catalogImportType) {
        return definitionOf(catalogImportType).layout().templateFilename();
    }

    @Override
    public CatalogImportReportResponse previewImport(CatalogImportType catalogImportType, byte[] content) {
        ICatalogImportDefinition catalogImportDefinition = definitionOf(catalogImportType);
        return CatalogImportReportResponse.of(analyze(catalogImportDefinition, read(catalogImportDefinition, content)));
    }

    @Override
    public CatalogImportReportResponse importRows(CatalogImportType catalogImportType, byte[] content) {
        ICatalogImportDefinition catalogImportDefinition = definitionOf(catalogImportType);
        List<CatalogImportRow> catalogImportRows = read(catalogImportDefinition, content);
        Map<Integer, CatalogImportRow> catalogImportRowsByNumber = catalogImportRows.stream()
                .collect(Collectors.toMap(CatalogImportRow::rowNumber, catalogImportRow -> catalogImportRow));
        return CatalogImportReportResponse.of(analyze(catalogImportDefinition, catalogImportRows).stream()
                .map(catalogImportRowResponse -> createWhenValid(
                        catalogImportDefinition,
                        catalogImportRowsByNumber.get(catalogImportRowResponse.rowNumber()),
                        catalogImportRowResponse))
                .toList());
    }

    @Override
    public byte[] exportRows(CatalogImportType catalogImportType, byte[] content, Collection<Integer> rowNumbers) {
        ICatalogImportDefinition catalogImportDefinition = definitionOf(catalogImportType);
        List<CatalogImportRow> catalogImportRows = read(catalogImportDefinition, content);
        Map<Integer, List<String>> errorsByRowNumber = analyze(catalogImportDefinition, catalogImportRows).stream()
                .collect(Collectors.toMap(CatalogImportRowResponse::rowNumber, CatalogImportRowResponse::errors));
        return catalogImportWorkbook.writeRejectedRows(
                catalogImportDefinition.layout(),
                catalogImportRows.stream()
                        .filter(catalogImportRow -> rowNumbers.contains(catalogImportRow.rowNumber()))
                        .toList(),
                errorsByRowNumber,
                catalogImportDefinition.existingNames());
    }

    private ICatalogImportDefinition definitionOf(CatalogImportType catalogImportType) {
        return catalogImportDefinitions.stream()
                .filter(catalogImportDefinition -> catalogImportDefinition.type() == catalogImportType)
                .findFirst()
                .orElseThrow();
    }

    private List<CatalogImportRow> read(ICatalogImportDefinition catalogImportDefinition, byte[] content) {
        return catalogImportWorkbook.read(content, catalogImportDefinition.layout());
    }

    private List<CatalogImportRowResponse> analyze(
            ICatalogImportDefinition catalogImportDefinition, List<CatalogImportRow> catalogImportRows) {
        Set<String> existingNames = new HashSet<>(catalogImportDefinition.existingNames().stream()
                .map(ExcelSheets::normalizeName)
                .toList());
        Map<String, Integer> rowNumbersByName = new HashMap<>();
        List<CatalogImportRowResponse> catalogImportRowResponses = new ArrayList<>();
        for (CatalogImportRow catalogImportRow : catalogImportRows) {
            String name = ExcelSheets.normalizeName(catalogImportRow.value(0));
            List<String> errors = catalogImportDefinition.validate(catalogImportRow);
            ImportRowStatus importRowStatus = ImportRowStatus.VALID;
            if (!errors.isEmpty()) {
                importRowStatus = ImportRowStatus.ERROR;
            } else if (existingNames.contains(name)) {
                importRowStatus = ImportRowStatus.DUPLICATE;
                errors = List.of("Đã có tên này trong hệ thống");
            } else {
                Integer firstRowNumber = rowNumbersByName.putIfAbsent(name, catalogImportRow.rowNumber());
                if (firstRowNumber != null) {
                    importRowStatus = ImportRowStatus.DUPLICATE;
                    errors = List.of("Trùng tên với dòng " + firstRowNumber);
                }
            }
            catalogImportRowResponses.add(new CatalogImportRowResponse(
                    catalogImportRow.rowNumber(),
                    catalogImportRow.value(0),
                    catalogImportDefinition.describe(catalogImportRow),
                    importRowStatus,
                    errors));
        }
        return catalogImportRowResponses;
    }

    private static CatalogImportRowResponse createWhenValid(
            ICatalogImportDefinition catalogImportDefinition,
            CatalogImportRow catalogImportRow,
            CatalogImportRowResponse catalogImportRowResponse) {
        if (catalogImportRowResponse.rowStatus() != ImportRowStatus.VALID) {
            return catalogImportRowResponse;
        }
        ImportRowStatus importRowStatus = ImportRowStatus.CREATED;
        List<String> errors = List.of();
        try {
            catalogImportDefinition.create(catalogImportRow);
        } catch (AppException appException) {
            importRowStatus = ImportRowStatus.ERROR;
            errors = List.of(appException.getMessage());
        }
        return new CatalogImportRowResponse(
                catalogImportRowResponse.rowNumber(),
                catalogImportRowResponse.name(),
                catalogImportRowResponse.detail(),
                importRowStatus,
                errors);
    }
}
