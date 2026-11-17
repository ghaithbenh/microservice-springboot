package tn.kira.louage.tripservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import tn.kira.louage.tripservice.entity.Trip;
import tn.kira.louage.tripservice.repository.TripRepository;

/**
 * Applies a join (one seat) under optimistic locking. The louage capacity is
 * resolved by the caller before this service runs, so no remote (Feign) call is
 * ever made while a database transaction is open.
 */
@Service
public class TripJoinService {

    static final int MAX_RETRIES = 5;

    private final TripRepository repository;
    private final TransactionTemplate transactionTemplate;

    public TripJoinService(TripRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public JoinOutcome join(Long id, int capacity) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status -> doJoin(id, capacity));
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (attempt == MAX_RETRIES) {
                    break;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Could not join trip " + id + ": too many concurrent updates, please retry");
    }

    private JoinOutcome doJoin(Long id, int capacity) {
        Trip trip = repository.findById(id).orElse(null);
        if (trip == null) {
            return new JoinOutcome(JoinStatus.NOT_FOUND, null);
        }
        if (trip.getStatus() == Trip.Status.DEPARTED) {
            return new JoinOutcome(JoinStatus.DEPARTED, trip);
        }
        if (trip.getSeatsTaken() >= capacity) {
            trip.setStatus(Trip.Status.DEPARTED);
            repository.saveAndFlush(trip);
            return new JoinOutcome(JoinStatus.FULL, trip);
        }
        trip.setSeatsTaken(trip.getSeatsTaken() + 1);
        if (trip.getSeatsTaken() >= capacity) {
            trip.setStatus(Trip.Status.DEPARTED);
        }
        Trip saved = repository.saveAndFlush(trip);
        return new JoinOutcome(JoinStatus.SUCCESS, saved);
    }

    public enum JoinStatus {
        SUCCESS,
        NOT_FOUND,
        DEPARTED,
        FULL
    }

    public record JoinOutcome(JoinStatus status, Trip trip) {
    }
}
