package com.cinema.modules.movie.enums;

public enum AgeRating {
    P(0, "Phổ biến mọi lứa tuổi"),
    C13(13, "Cấm khán giả dưới 13 tuổi"),
    C16(16, "Cấm khán giả dưới 16 tuổi"),
    C18(18, "Cấm khán giả dưới 18 tuổi");

    private final int minimumAge;
    private final String description;

    AgeRating(int minimumAge, String description) {
        this.minimumAge = minimumAge;
        this.description = description;
    }

    public int getMinimumAge() {
        return minimumAge;
    }

    public String getDescription() {
        return description;
    }
}
