package project.labadvancedprogramming.ApiGateway.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CountContestDTO {
    private int contest_id;    
    private int challenge_count;
}
