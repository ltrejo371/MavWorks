package com.mavworks.repository;

import com.mavworks.model.Availability;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
    List<Availability> findByUserIdAndDateBetween(Long userId, LocalDate first, LocalDate last);
    List<Availability> findByUserIdAndDate(Long userId, LocalDate date);
    void deleteByUserIdAndDateAndSlot(Long userId, LocalDate date, int slot);
}
