package com.cinema.modules.auth.scope;

import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class CinemaScopeResolver {

    private final IBranchAccessService branchAccessService;

    public CinemaScope resolve(Long userId, Long requestedCinemaId) {
        if (requestedCinemaId != null) {
            branchAccessService.assertCanAccessCinema(userId, requestedCinemaId);
            return new CinemaScope(false, Set.of(requestedCinemaId));
        }
        if (branchAccessService.hasAccessToAllCinemas(userId)) {
            return new CinemaScope(true, Set.of());
        }
        return new CinemaScope(false, branchAccessService.getAccessibleCinemaIds(userId));
    }
}
