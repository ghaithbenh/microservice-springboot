package tn.kira.louage.stationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.kira.louage.stationservice.entity.Station;

public interface StationRepository extends JpaRepository<Station, Long> {
}
