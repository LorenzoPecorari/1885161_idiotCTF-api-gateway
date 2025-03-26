package project.labadvancedprogramming.ApiGateway.util;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;

public interface  SubmissionClient {
    @RequestLine("GET /submissions/getbyuseridandcontestid/{user_id}/{contest_id}")
    @Headers("Content-Type: application/json")
    Response get_submissions_by_user_id_and_contest_id(@Param("user_id") int user_id,@Param("contest_id") int contest_id);

    @RequestLine("POST /submissions")
    @Headers("Content-Type: application/json")
    Response create_submission(SubmissionDTO submissionDTO);
}