package com.cinema.modules.movie.controller;

import com.cinema.common.exception.AppException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

final class ImportFiles {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private ImportFiles() {
    }

    static byte[] readXlsx(MultipartFile multipartFile) {
        String filename = multipartFile.getOriginalFilename();
        if (multipartFile.isEmpty() || filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new AppException("Hãy chọn tệp Excel định dạng .xlsx", HttpStatus.BAD_REQUEST, "INVALID_IMPORT_FILE");
        }
        try {
            return multipartFile.getBytes();
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    static ResponseEntity<byte[]> toXlsxResponse(byte[] content, String filename) {
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }
}
