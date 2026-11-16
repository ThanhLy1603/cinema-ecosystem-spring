package com.cinema.modules.showtime.service;

import com.cinema.common.exception.AppException;
import com.cinema.common.util.BusinessZone;
import com.cinema.modules.audit.enums.AuditAction;
import com.cinema.modules.audit.service.PriceChangeAuditor;
import com.cinema.modules.auth.service.interfaces.IBranchAccessService;
import com.cinema.modules.cinema.dto.response.HallInfoResponse;
import com.cinema.modules.cinema.service.interfaces.ICinemaQueryService;
import com.cinema.modules.movie.dto.response.MovieInfoResponse;
import com.cinema.modules.movie.service.interfaces.IMovieQueryService;
import com.cinema.modules.showtime.dto.request.ShowtimeRequest;
import com.cinema.modules.showtime.dto.response.ShowtimeResponse;
import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.model.PlannedShowtimeSlot;
import com.cinema.modules.showtime.enums.ShowtimeStatus;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import com.cinema.modules.showtime.service.interfaces.IShowtimeAdminService;
import com.cinema.modules.showtime.specification.ShowtimeSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShowtimeAdminService implements IShowtimeAdminService {

    private static final Sort SHOWTIME_SORT = Sort.by("startTime", "id");

    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeSlotPlanner showtimeSlotPlanner;
    private final ShowtimePersister showtimePersister;
    private final ShowtimeBookingChecker showtimeBookingChecker;
    private final ShowtimeViewAssembler showtimeViewAssembler;
    private final ICinemaQueryService cinemaQueryService;
    private final IMovieQueryService movieQueryService;
    private final IBranchAccessService branchAccessService;
    private final Clock clock;
    private final PriceChangeAuditor priceChangeAuditor;
    @Override
    public List<ShowtimeResponse> listShowtimes(
            Long actorUserId, Long cinemaId, LocalDate date, Long movieId, ShowtimeStatus showtimeStatus) {
        branchAccessService.assertCanAccessCinema(actorUserId, cinemaId);
        if (!cinemaQueryService.findAllCinemaIds().contains(cinemaId)) {
            throw new AppException("Không tìm thấy cụm rạp", HttpStatus.NOT_FOUND, "CINEMA_NOT_FOUND");
        }
        Set<Long> hallIds = cinemaQueryService.findHallIdsByCinemaIds(List.of(cinemaId));
        if (hallIds.isEmpty()) {
            return List.of();
        }
        Specification<Showtime> specification = Specification.where(ShowtimeSpecifications.inHalls(hallIds))
                .and(ShowtimeSpecifications.overlapping(BusinessZone.startOfDay(date), BusinessZone.startOfNextDay(date)))
                .and(ShowtimeSpecifications.withMovie(movieId))
                .and(ShowtimeSpecifications.withStatus(showtimeStatus));
        return showtimeViewAssembler.toShowtimeResponses(
                showtimeRepository.findAll(specification, SHOWTIME_SORT), Instant.now(clock));
    }

    @Override
    public ShowtimeResponse getShowtime(Long actorUserId, Long showtimeId) {
        Showtime showtime = loadShowtime(showtimeId);
        assertCanAccessShowtime(actorUserId, showtime);
        return showtimeViewAssembler.toShowtimeResponses(List.of(showtime), Instant.now(clock)).get(0);
    }

    @Override
    public ShowtimeResponse createShowtime(Long actorUserId, ShowtimeRequest showtimeRequest) {
        Instant now = Instant.now(clock);
        PlannedShowtimeSlot showtimeSlot = planSlot(actorUserId, showtimeRequest, now, null);
        Showtime showtime = new Showtime();
        applySlot(showtime, showtimeRequest, showtimeSlot);
        Showtime savedShowtime = showtimePersister.saveWithoutOverlap(showtime, showtimeSlot, null);
        return showtimeViewAssembler.toShowtimeResponses(List.of(savedShowtime), now).get(0);
    }

    @Override
    public ShowtimeResponse updateShowtime(Long actorUserId, Long showtimeId, ShowtimeRequest showtimeRequest) {
        Instant now = Instant.now(clock);
        Showtime showtime = loadShowtime(showtimeId);
        assertCanAccessShowtime(actorUserId, showtime);
        assertActive(showtime);
        if (!showtime.getStartTime().isAfter(now)) {
            throw new AppException("Suất chiếu đã bắt đầu, không thể chỉnh sửa", HttpStatus.CONFLICT, "SHOWTIME_STARTED");
        }
        assertNoBookings(showtime, now, "Suất chiếu đã có vé hoặc ghế đang được giữ, không thể chỉnh sửa");
        PlannedShowtimeSlot showtimeSlot = planSlot(actorUserId, showtimeRequest, now, showtimeId);
        BigDecimal oldBasePrice = showtime.getBasePrice();
        applySlot(showtime, showtimeRequest, showtimeSlot);
        Showtime savedShowtime = showtimePersister.saveWithoutOverlap(showtime, showtimeSlot, showtimeId);
        recordBasePriceChange(savedShowtime, oldBasePrice);
        return showtimeViewAssembler.toShowtimeResponses(List.of(savedShowtime), now).get(0);
    }

    @Override
    public ShowtimeResponse cancelShowtime(Long actorUserId, Long showtimeId) {
        Instant now = Instant.now(clock);
        Showtime showtime = loadShowtime(showtimeId);
        assertCanAccessShowtime(actorUserId, showtime);
        assertActive(showtime);
        if (!showtime.getEndTime().isAfter(now)) {
            throw new AppException("Suất chiếu đã kết thúc, không thể hủy", HttpStatus.CONFLICT, "SHOWTIME_ENDED");
        }
        assertNoBookings(showtime, now, "Suất chiếu đã có vé hoặc ghế đang được giữ, hãy hoàn vé trước khi hủy suất");
        showtime.setStatus(ShowtimeStatus.CANCELLED);
        Showtime savedShowtime = showtimeRepository.saveAndFlush(showtime);
        return showtimeViewAssembler.toShowtimeResponses(List.of(savedShowtime), now).get(0);
    }

    @Override
    public List<MovieInfoResponse> listSchedulableMovies() {
        return movieQueryService.findSchedulableMovieInfos();
    }

    private PlannedShowtimeSlot planSlot(
            Long actorUserId, ShowtimeRequest showtimeRequest, Instant now, Long excludedShowtimeId) {
        return showtimeSlotPlanner.plan(
                actorUserId,
                showtimeRequest.movieId(),
                showtimeRequest.hallId(),
                showtimeRequest.startTime(),
                now,
                excludedShowtimeId);
    }

    private void applySlot(Showtime showtime, ShowtimeRequest showtimeRequest, PlannedShowtimeSlot showtimeSlot) {
        showtime.setMovieId(showtimeRequest.movieId());
        showtime.setHallId(showtimeRequest.hallId());
        showtime.setStartTime(showtimeSlot.startTime());
        showtime.setEndTime(showtimeSlot.endTime());
        showtime.setBasePrice(showtimeRequest.basePrice());
        showtime.setStatus(ShowtimeStatus.ACTIVE);
    }

    private void recordBasePriceChange(Showtime showtime, BigDecimal oldBasePrice) {
        HallInfoResponse hallInfoResponse =
                cinemaQueryService.findHallInfos(List.of(showtime.getHallId())).get(showtime.getHallId());
        priceChangeAuditor.recordPriceChange(
                AuditAction.SHOWTIME_PRICE_CHANGED,
                hallInfoResponse == null ? null : hallInfoResponse.cinemaId(),
                "SHOWTIME",
                showtime.getId(),
                "Giá cơ bản suất chiếu " + showtime.getId(),
                oldBasePrice,
                showtime.getBasePrice());
    }

    private Showtime loadShowtime(Long showtimeId) {
        return showtimeRepository.findById(showtimeId).orElseThrow(
                () -> new AppException("Không tìm thấy suất chiếu", HttpStatus.NOT_FOUND, "SHOWTIME_NOT_FOUND"));
    }

    private void assertCanAccessShowtime(Long actorUserId, Showtime showtime) {
        HallInfoResponse hallInfoResponse =
                cinemaQueryService.findHallInfos(List.of(showtime.getHallId())).get(showtime.getHallId());
        branchAccessService.assertCanAccessCinema(actorUserId, hallInfoResponse.cinemaId());
    }

    private void assertActive(Showtime showtime) {
        if (showtime.getStatus() != ShowtimeStatus.ACTIVE) {
            throw new AppException("Suất chiếu đã bị hủy", HttpStatus.CONFLICT, "SHOWTIME_CANCELLED");
        }
    }

    private void assertNoBookings(Showtime showtime, Instant now, String message) {
        if (!showtimeBookingChecker.findShowtimeIdsWithBookings(List.of(showtime.getId()), now).isEmpty()) {
            throw new AppException(message, HttpStatus.CONFLICT, "SHOWTIME_HAS_BOOKINGS");
        }
    }
}
