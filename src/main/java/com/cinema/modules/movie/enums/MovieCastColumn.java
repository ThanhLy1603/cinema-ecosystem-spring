package com.cinema.modules.movie.enums;

public enum MovieCastColumn {
    MOVIE_TITLE("Tên phim", true, 40),
    PERSON_NAME("Nghệ sĩ", true, 34),
    ROLE("Vai trò", true, 22),
    CHARACTER_NAME("Tên nhân vật", false, 34);

    private final String header;
    private final boolean required;
    private final int width;

    MovieCastColumn(String header, boolean required, int width) {
        this.header = header;
        this.required = required;
        this.width = width;
    }

    public String getHeader() {
        return header;
    }

    public boolean isRequired() {
        return required;
    }

    public int getWidth() {
        return width;
    }
}
