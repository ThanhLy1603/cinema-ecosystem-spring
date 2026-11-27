package com.cinema.modules.movie.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.movie.dto.response.CatalogImportReportResponse;
import com.cinema.modules.movie.enums.CatalogImportType;
import com.cinema.modules.movie.service.interfaces.ICatalogImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/{catalog:genres|persons}/import")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
public class AdminCatalogImportController {

    private final ICatalogImportService catalogImportService;

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String catalog) {
        CatalogImportType catalogImportType = CatalogImportType.fromPath(catalog);
        return ImportFiles.toXlsxResponse(
                catalogImportService.buildTemplate(catalogImportType),
                catalogImportService.templateFilename(catalogImportType));
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CatalogImportReportResponse>> previewImport(
            @PathVariable String catalog, @RequestPart("file") MultipartFile multipartFile) {
        return ResponseEntity.ok(ApiResponse.success(catalogImportService.previewImport(
                CatalogImportType.fromPath(catalog), ImportFiles.readXlsx(multipartFile))));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CatalogImportReportResponse>> importRows(
            @PathVariable String catalog, @RequestPart("file") MultipartFile multipartFile) {
        CatalogImportReportResponse catalogImportReportResponse = catalogImportService.importRows(
                CatalogImportType.fromPath(catalog), ImportFiles.readXlsx(multipartFile));
        return ResponseEntity.ok(ApiResponse.success(
                "Đã tạo " + catalogImportReportResponse.createdRows() + " mục", catalogImportReportResponse));
    }

    @PostMapping(path = "/rows", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> exportRows(
            @PathVariable String catalog,
            @RequestPart("file") MultipartFile multipartFile,
            @RequestParam List<Integer> rowNumbers) {
        return ImportFiles.toXlsxResponse(
                catalogImportService.exportRows(
                        CatalogImportType.fromPath(catalog), ImportFiles.readXlsx(multipartFile), rowNumbers),
                "dong-can-sua.xlsx");
    }
}
