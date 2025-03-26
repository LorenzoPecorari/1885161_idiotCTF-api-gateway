package project.labadvancedprogramming.ApiGateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.okhttp.OkHttpClient;
import project.labadvancedprogramming.ApiGateway.util.ChallengeClient;
import project.labadvancedprogramming.ApiGateway.util.ContestClient;
import project.labadvancedprogramming.ApiGateway.util.SubmissionClient;
import project.labadvancedprogramming.ApiGateway.util.UserClient;

@Configuration
public class FeignClientConfig {

    @Value("${spring.cloud.gateway.routes[0].uri}")
    private String authHostPort;
    
    @Value("${spring.cloud.gateway.routes[1].uri}")
    private String challengeHostPort;

    @Value("${spring.cloud.gateway.routes[2].uri}")
    private String contestHostPort;

    @Value("${spring.cloud.gateway.routes[3].uri}")
    private String submissionHostPort;

    @Bean
    public UserClient userClient(){
        return Feign.builder()
            .client(new OkHttpClient())
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(UserClient.class, authHostPort);
    }

    @Bean
    public ContestClient contestClient(){
        return Feign.builder()
            .client(new OkHttpClient())
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(ContestClient.class, contestHostPort);
    }

    @Bean
    public ChallengeClient challengeClient(){
        return Feign.builder()
            .client(new OkHttpClient())
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(ChallengeClient.class, challengeHostPort);
    }

    @Bean
    public SubmissionClient submissionClient(){
        return Feign.builder()
            .client(new OkHttpClient())
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(SubmissionClient.class, submissionHostPort);
    }
}
