package com.cinema.modules.movie.model;

import java.util.List;

public record MovieCastPlan(List<PlannedMovieCast> plannedMovieCasts, List<String> errors) {

    public static final MovieCastPlan EMPTY = new MovieCastPlan(List.of(), List.of());

    public List<String> newPersonNames() {
        return plannedMovieCasts.stream()
                .filter(plannedMovieCast -> plannedMovieCast.personId() == null)
                .map(PlannedMovieCast::personName)
                .distinct()
                .toList();
    }
}
