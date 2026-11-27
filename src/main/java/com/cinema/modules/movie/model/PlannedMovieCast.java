package com.cinema.modules.movie.model;

import com.cinema.modules.movie.enums.CastRoleType;

public record PlannedMovieCast(Long personId, String personName, CastRoleType roleType, String characterName) {
}
