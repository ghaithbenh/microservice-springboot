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

    @Column(nullable = false)
    private Long louageId;

    @Column(nullable = false)
    private Long departureStationId;

    @Column(nullable = false)
    private Long destinationStationId;

    @Column(nullable = false)
    private int seatsTaken;

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
