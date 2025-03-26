package project.labadvancedprogramming.ApiGateway.util;

import java.util.ArrayList;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContestDTO {
    private int id;
    private String name;
    private int admin_id;
    private String start_datetime;
    private String end_datetime;
    private ArrayList<String> participants;
}
