package tn.kira.louage.louageservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.kira.louage.louageservice.entity.Louage;

public interface LouageRepository extends JpaRepository<Louage, Long> {

    boolean existsByPlateNumber(String plateNumber);
}
