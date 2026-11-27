package com.cinema.modules.movie.enums;

public enum MovieImportColumn {
    TITLE("Tên phim", "title", true, 36),
    SLUG("Đường dẫn", "slug", false, 28),
    ORIGINAL_TITLE("Tên gốc", "originalTitle", false, 28),
    DURATION("Thời lượng phút", "durationMinutes", true, 24),
    RELEASE_DATE("Ngày khởi chiếu", "releaseDate", true, 24),
    END_DATE("Ngày kết thúc", "endDate", false, 18),
    RATED("Nhãn độ tuổi", "rated", true, 32),
    STATUS("Trạng thái", "status", false, 16),
    GENRE_1("Thể loại 1", "genreIds", false, 26),
    GENRE_2("Thể loại 2", "genreIds", false, 26),
    GENRE_3("Thể loại 3", "genreIds", false, 26),
    OTHER_GENRES("Thể loại khác", "genreIds", false, 34),
    DESCRIPTION("Mô tả", "description", false, 50),
    POSTER("Poster", "posterUrl", false, 36),
    BANNER("Banner", "bannerUrl", false, 36),
    TRAILER("Trailer", "trailerUrl", false, 36);

    private final String header;
    private final String fieldName;
    private final boolean required;
    private final int width;

    MovieImportColumn(String header, String fieldName, boolean required, int width) {
        this.header = header;
        this.fieldName = fieldName;
        this.required = required;
        this.width = width;
    }

    public String getHeader() {
        return header;
    }

    public String getFieldName() {
        return fieldName;
    }

    public boolean isRequired() {
        return required;
    }

    public int getWidth() {
        return width;
    }
}
