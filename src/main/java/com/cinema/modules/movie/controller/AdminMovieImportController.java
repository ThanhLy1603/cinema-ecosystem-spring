package com.cinema.modules.movie.controller;

import com.cinema.common.response.ApiResponse;
import com.cinema.modules.movie.dto.response.MovieImportReportResponse;
import com.cinema.modules.movie.service.interfaces.IMovieImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/movies/import")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
public class AdminMovieImportController {

    private final IMovieImportService movieImportService;

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        return ImportFiles.toXlsxResponse(movieImportService.buildTemplate(), "mau-nhap-phim.xlsx");
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MovieImportReportResponse>> previewImport(
            @RequestPart("file") MultipartFile multipartFile) {
        return ResponseEntity.ok(ApiResponse.success(movieImportService.previewImport(ImportFiles.readXlsx(multipartFile))));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MovieImportReportResponse>> importMovies(
            @RequestPart("file") MultipartFile multipartFile) {
        MovieImportReportResponse movieImportReportResponse =
                movieImportService.importMovies(ImportFiles.readXlsx(multipartFile));
        return ResponseEntity.ok(ApiResponse.success(
                "Đã tạo " + movieImportReportResponse.createdRows() + " phim", movieImportReportResponse));
    }

    @PostMapping(path = "/rows", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> exportRows(
            @RequestPart("file") MultipartFile multipartFile, @RequestParam List<Integer> rowNumbers) {
        return ImportFiles.toXlsxResponse(
                movieImportService.exportRows(ImportFiles.readXlsx(multipartFile), rowNumbers), "phim-can-sua.xlsx");
    }
}
