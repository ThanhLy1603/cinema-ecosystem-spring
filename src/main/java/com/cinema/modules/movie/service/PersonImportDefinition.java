package com.cinema.modules.movie.service;

import com.cinema.modules.movie.dto.request.PersonRequest;
import com.cinema.modules.movie.entity.Person;
import com.cinema.modules.movie.enums.CatalogImportType;
import com.cinema.modules.movie.model.CatalogImportColumn;
import com.cinema.modules.movie.model.CatalogImportLayout;
import com.cinema.modules.movie.model.CatalogImportRow;
import com.cinema.modules.movie.repository.PersonRepository;
import com.cinema.modules.movie.service.interfaces.ICatalogImportDefinition;
import com.cinema.modules.movie.service.interfaces.IPersonAdminService;
import com.cinema.modules.movie.util.ExcelSheets;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PersonImportDefinition implements ICatalogImportDefinition {

    private static final CatalogImportLayout LAYOUT = new CatalogImportLayout(
            "Nghệ sĩ",
            List.of(
                    new CatalogImportColumn("Tên nghệ sĩ", true, 36),
                    new CatalogImportColumn("Ảnh đại diện", false, 50),
                    new CatalogImportColumn("Tiểu sử", false, 70)),
            List.of(
                    List.of(
                            ExcelSheets.SAMPLE_PREFIX + " Trấn Thành",
                            "https://example.com/tran-thanh.jpg",
                            "Đạo diễn, diễn viên người Việt Nam."),
                    List.of(ExcelSheets.SAMPLE_PREFIX + " Phương Anh Đào", "", "")),
            "Nghệ sĩ đang có",
            List.of(
                    "Mỗi dòng ở sheet Nghệ sĩ là một người, tối đa 500 dòng mỗi lần.",
                    "Cột có dấu (*) màu đỏ là bắt buộc. Tên tối đa 150 ký tự, tiểu sử tối đa 2000 ký tự.",
                    "Dòng có tên bắt đầu bằng [Mẫu] chỉ để tham khảo, hệ thống không tạo.",
                    "Ảnh đại diện có thể để trống. Nếu điền, đường dẫn bắt đầu bằng https://.",
                    "Tên trùng với nghệ sĩ đã có sẽ được bỏ qua. Người trùng tên thật thì thêm bằng nút Thêm nghệ sĩ."),
            "mau-nhap-nghe-si.xlsx");

    private final PersonRepository personRepository;
    private final IPersonAdminService personAdminService;
    private final Validator validator;

    @Override
    public CatalogImportType type() {
        return CatalogImportType.PERSONS;
    }

    @Override
    public CatalogImportLayout layout() {
        return LAYOUT;
    }

    @Override
    public List<String> existingNames() {
        return personRepository.findAll(Sort.by("name", "id")).stream().map(Person::getName).toList();
    }

    @Override
    public List<String> validate(CatalogImportRow catalogImportRow) {
        return validator.validate(toPersonRequest(catalogImportRow)).stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .toList();
    }

    @Override
    public String describe(CatalogImportRow catalogImportRow) {
        return catalogImportRow.value(2);
    }

    @Override
    public void create(CatalogImportRow catalogImportRow) {
        personAdminService.createPerson(toPersonRequest(catalogImportRow));
    }

    private static PersonRequest toPersonRequest(CatalogImportRow catalogImportRow) {
        return new PersonRequest(
                catalogImportRow.value(0), catalogImportRow.value(1), catalogImportRow.value(2));
    }
}
