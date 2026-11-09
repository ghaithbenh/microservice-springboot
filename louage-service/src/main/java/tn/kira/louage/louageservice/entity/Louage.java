package tn.kira.louage.louageservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "louages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Louage {

    public enum Status {
        AVAILABLE,
        ON_ROAD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)//the database generates it with auto-increment (1, 2, 3...)
    private Long id;

    @NotBlank(message = "plateNumber is required")
    @Column(nullable = false, unique = true)
    private String plateNumber;

    @Positive(message = "capacity must be positive")
    @Column(nullable = false)
    private int capacity;

    @NotBlank(message = "driverName is required")
    @Column(nullable = false)
    private String driverName;

    // @Enumerated(EnumType.STRING) stores the text "AVAILABLE" in the DB. The alternative (ORDINAL) stores 0 or 1, which breaks silently if someone reorders the enum.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
}
