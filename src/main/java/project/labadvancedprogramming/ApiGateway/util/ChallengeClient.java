package project.labadvancedprogramming.ApiGateway.util;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;

public interface ChallengeClient {
    @RequestLine("GET /challenges/{challenge_id}")
    @Headers("Content-Type: application/json")
    Response challenge_by_id(@Param("challenge_id") int challenge_id);

    @RequestLine("GET /challenges/count_contests")
    @Headers("Content-Type: application/json")
    Response count_contests();
}