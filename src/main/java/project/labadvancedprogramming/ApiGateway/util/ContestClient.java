package project.labadvancedprogramming.ApiGateway.util;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;

public interface ContestClient{

    @RequestLine("POST /contests")
    @Headers("Content-Type: application/json")
    Response create_contest(ContestDTO contestDTO);

    @RequestLine("POST /contests/{id}/add_new_partecipant")
    @Headers("Content-Type: application/json")
    Response add_contestant(@Param("id") int id, ContestUserDTO contestUserDTO);

    @RequestLine("GET /contests/users")
    @Headers("Content-Type: application/json")
    Response get_contests_user();

    @RequestLine("PUT /contests/{contest_id}")
    @Headers("Content-Type: application/json")
    Response update_contest(@Param("contest_id") int contest_id, ContestDTO contestDTO);

    @RequestLine("GET /contests/getcontestsbyuser/{user_id}")
    @Headers("Content-Type: application/json")
    Response get_contests_by_user(@Param("user_id") int user_id);

    @RequestLine("GET /contests/get_user_by_email/{email}")
    @Headers("Content-Type: application/json")
    Response get_user(@Param("email") String email);

    @RequestLine("GET /contests/{contest_id}")
    @Headers("Content-Type: application/json")
    Response get_contest(@Param("contest_id") int contest_id);
}