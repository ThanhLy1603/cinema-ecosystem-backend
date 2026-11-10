package com.cinema.modules.showtime.service;

import com.cinema.modules.showtime.entity.Showtime;
import com.cinema.modules.showtime.exception.ShowtimeConflictException;
import com.cinema.modules.showtime.model.PlannedShowtimeSlot;
import com.cinema.modules.showtime.repository.ShowtimeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class ShowtimePersister {

    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeConflictDetector showtimeConflictDetector;

    public ShowtimePersister(ShowtimeRepository showtimeRepository, ShowtimeConflictDetector showtimeConflictDetector) {
        this.showtimeRepository = showtimeRepository;
        this.showtimeConflictDetector = showtimeConflictDetector;
    }

    public Showtime saveWithoutOverlap(Showtime showtime, PlannedShowtimeSlot plannedShowtimeSlot, Long excludedShowtimeId) {
        try {
            return showtimeRepository.saveAndFlush(showtime);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            if (!showtimeConflictDetector.isOverlapViolation(dataIntegrityViolationException)) {
                throw dataIntegrityViolationException;
            }
            throw new ShowtimeConflictException(showtimeConflictDetector.findConflicts(
                    showtime.getHallId(),
                    plannedShowtimeSlot.startTime(),
                    plannedShowtimeSlot.endTime(),
                    excludedShowtimeId));
        }
    }
}
