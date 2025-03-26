package project.labadvancedprogramming.ApiGateway.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserDTO {
    private Long matricola;
    private String name;
    private String surname;
    private String email;
    private String gender;
    private String dob;
    private String password;
    private String university;
    private String role;
}
