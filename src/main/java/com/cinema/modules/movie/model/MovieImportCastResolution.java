package com.cinema.modules.movie.model;

import java.util.List;
import java.util.Map;

public record MovieImportCastResolution(
        Map<Integer, MovieCastPlan> movieCastPlansByRowNumber, List<String> unmatchedErrors) {

    public MovieCastPlan planFor(int movieRowNumber) {
        return movieCastPlansByRowNumber.getOrDefault(movieRowNumber, MovieCastPlan.EMPTY);
    }
}
