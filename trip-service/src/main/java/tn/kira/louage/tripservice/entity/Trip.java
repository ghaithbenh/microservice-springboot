package tn.kira.louage.tripservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tn.kira.louage.tripservice.dto.Louage;
import tn.kira.louage.tripservice.dto.Station;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    public enum Status {
        WAITING,
        DEPARTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "louageId is required")
    @Column(nullable = false)
    private Long louageId;

    @NotNull(message = "departureStationId is required")
    @Column(nullable = false)
    private Long departureStationId;

    @NotNull(message = "destinationStationId is required")
    @Column(nullable = false)
    private Long destinationStationId;

    @PositiveOrZero(message = "seatsTaken must be zero or positive")
    @Column(nullable = false)
    private int seatsTaken;

    @Positive(message = "pricePerSeat must be positive")
    @Column(nullable = false)
    private double pricePerSeat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Transient
    private Louage louage;

    @Transient
    private Station departureStation;

    @Transient
    private Station destinationStation;
}
