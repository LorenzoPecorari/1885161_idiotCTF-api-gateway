package project.labadvancedprogramming.ApiGateway.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionDTO {
    private int id;
    private int challenge_id;
    private int contest_id;
    private int user_id;
    private String user_email;
    private String submission_datetime;
    private String submitted_flag;
    private boolean solved;
}