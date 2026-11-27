package com.cinema.modules.movie.service;

import com.cinema.modules.movie.dto.request.GenreRequest;
import com.cinema.modules.movie.entity.Genre;
import com.cinema.modules.movie.enums.CatalogImportType;
import com.cinema.modules.movie.model.CatalogImportColumn;
import com.cinema.modules.movie.model.CatalogImportLayout;
import com.cinema.modules.movie.model.CatalogImportRow;
import com.cinema.modules.movie.repository.GenreRepository;
import com.cinema.modules.movie.service.interfaces.ICatalogImportDefinition;
import com.cinema.modules.movie.service.interfaces.IGenreAdminService;
import com.cinema.modules.movie.util.ExcelSheets;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GenreImportDefinition implements ICatalogImportDefinition {

    private static final CatalogImportLayout LAYOUT = new CatalogImportLayout(
            "Thể loại",
            List.of(new CatalogImportColumn("Tên thể loại", true, 40)),
            List.of(
                    List.of(ExcelSheets.SAMPLE_PREFIX + " Cổ Trang"),
                    List.of(ExcelSheets.SAMPLE_PREFIX + " Tài Liệu")),
            "Thể loại đang có",
            List.of(
                    "Mỗi dòng ở sheet Thể loại là một thể loại, tối đa 500 dòng mỗi lần.",
                    "Cột có dấu (*) màu đỏ là bắt buộc. Tên tối đa 100 ký tự.",
                    "Dòng có tên bắt đầu bằng [Mẫu] chỉ để tham khảo, hệ thống không tạo.",
                    "Tên trùng với thể loại đã có sẽ được bỏ qua, không phân biệt chữ hoa chữ thường.",
                    "Đường dẫn của thể loại được tự tạo từ tên."),
            "mau-nhap-the-loai.xlsx");

    private final GenreRepository genreRepository;
    private final IGenreAdminService genreAdminService;
    private final Validator validator;

    @Override
    public CatalogImportType type() {
        return CatalogImportType.GENRES;
    }

    @Override
    public CatalogImportLayout layout() {
        return LAYOUT;
    }

    @Override
    public List<String> existingNames() {
        return genreRepository.findAllByOrderByNameAsc().stream().map(Genre::getName).toList();
    }

    @Override
    public List<String> validate(CatalogImportRow catalogImportRow) {
        return validator.validate(toGenreRequest(catalogImportRow)).stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .toList();
    }

    @Override
    public String describe(CatalogImportRow catalogImportRow) {
        return "";
    }

    @Override
    public void create(CatalogImportRow catalogImportRow) {
        genreAdminService.createGenre(toGenreRequest(catalogImportRow));
    }

    private static GenreRequest toGenreRequest(CatalogImportRow catalogImportRow) {
        return new GenreRequest(catalogImportRow.value(0));
    }
}
