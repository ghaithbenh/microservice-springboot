package tn.kira.louage.tripservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.kira.louage.tripservice.entity.Trip;

public interface TripRepository extends JpaRepository<Trip, Long> {
}
