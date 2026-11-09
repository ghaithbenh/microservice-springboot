package tn.kira.louage.tripservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Louage {

    private Long id;
    private String plateNumber;
    private int capacity;
    private String driverName;
    private String status;
}
