package tn.kira.louage.authservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.kira.louage.authservice.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
