package project.labadvancedprogramming.ApiGateway.util;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;

public interface UserClient {
    
    @RequestLine("GET /user/getuserrole")
    @Headers({"Authorization: {token}", "Content-Type: application/json"})
    Response getUserRole(@Param("token") String token);

    @RequestLine("GET /user/getusers")
    @Headers("Content-Type: application/json")
    Response get_user();
    
}