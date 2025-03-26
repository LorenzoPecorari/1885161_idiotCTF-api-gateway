package project.labadvancedprogramming.ApiGateway.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import project.labadvancedprogramming.ApiGateway.util.ChallengeClient;
import project.labadvancedprogramming.ApiGateway.util.ContestClient;
import project.labadvancedprogramming.ApiGateway.util.ContestDTO;
import project.labadvancedprogramming.ApiGateway.util.ContestUserDTO;
import project.labadvancedprogramming.ApiGateway.util.CountContestDTO;
import project.labadvancedprogramming.ApiGateway.util.SubmissionClient;
import project.labadvancedprogramming.ApiGateway.util.SubmissionDTO;
import project.labadvancedprogramming.ApiGateway.util.UserClient;
import project.labadvancedprogramming.ApiGateway.util.UserDTO;
import reactor.core.publisher.Mono;

@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    @Value("${security.endpoint.noauth}")
    private final List<String> noAuthEndPoints = List.of();

    @Value("${security.endpoint.student}")
    private final List<String> studentEndPoints = List.of();

    @Value("${security.endpoint.teacher}")
    private final List<String> teacherEndPoints = List.of();

    Logger log=LogManager.getLogger(CustomGlobalFilter.class);

    @Autowired
    private UserClient userClient;

    @Autowired
    private ContestClient contestClient;

    @Autowired
    private SubmissionClient submissionClient;

    @Autowired
    private  ChallengeClient challengeClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info( "Filter starts to work...");
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        Predicate<ServerHttpRequest> noAuthMatchers = ep -> noAuthEndPoints.stream().anyMatch(uri -> {
            return ep.getURI().getPath().contains(uri);
        });
        boolean isNoAuth = noAuthMatchers.test(request);

        Predicate<ServerHttpRequest> studentMatcher = ep -> studentEndPoints.stream().anyMatch(uri -> {
            String[] parts = uri.split(" ");
            String path = parts[0];
            HttpMethod method = null;
            if (parts.length == 2) {
                method = HttpMethod.valueOf(parts[1]);
            }
            boolean returnVal;
            if (method != null) {
            returnVal = ep.getURI().getPath().contains(path) && (ep.getMethod().equals(HttpMethod.GET) || ep.getMethod().equals(HttpMethod.POST));
            } else {
            returnVal = ep.getURI().getPath().contains(path);
            }
            return returnVal;
        });
        boolean isStudent = studentMatcher.test(request);

        Predicate<ServerHttpRequest> teacherMatcher = ep -> teacherEndPoints.stream().anyMatch(uri -> ep.getURI().getPath().contains(uri));
        boolean isTeacher = teacherMatcher.test(request);

        String uri = request.getURI().getPath();
        log.info("Uri received: " + uri);
        log.info("Method received: " + request.getMethod());
        noAuthEndPoints.forEach(matcher -> log.info("NoAuth matcher: " + matcher));
        log.info(uri +" is a IsNoAuth: " + isNoAuth);
        studentEndPoints.forEach(matcher -> log.info("Student matcher: " + matcher));
        log.info(uri +" is a IsStudent: " + isStudent);
        teacherEndPoints.forEach(matcher -> log.info("Teacher matcher: " + matcher));
        log.info(uri +" is a IsTeacher: " + isTeacher);
        if (isNoAuth) {
            log.info("NoAuth route matched");
            if(uri.contentEquals("/api/leaderboard")){
                ArrayList<Map.Entry<String, Integer>> leaderboard = new ArrayList<>();
                try {
                    String responseContestUser=new String(contestClient.get_contests_user().body().asInputStream().readAllBytes());
                    JsonNode nodeContestUser=objectMapper.readTree(responseContestUser).get("data");
                    int userNumber=nodeContestUser.get("count").asInt();
                    ArrayList<ContestUserDTO> contestUserDTOs=new ArrayList<>(userNumber);
                    nodeContestUser.get("objects").forEach(user->{
                        log.info("user: " + user);
                        ContestUserDTO contestUserDTO=new ContestUserDTO(user.get("id").asInt(), user.get("username").asText());
                        contestUserDTOs.add(contestUserDTO);
                    });

                    contestUserDTOs.forEach((contestUserDTO) -> {
                        Map.Entry<String, Integer> player = new AbstractMap.SimpleEntry<>(contestUserDTO.getUsername(), 0);
                        try {
                            String stringContestsByUser=new String(contestClient.get_contests_by_user(contestUserDTO.getId()).body().asInputStream().readAllBytes());
                            JsonNode nodeContestsByUser=objectMapper.readTree(stringContestsByUser).get("data");
                            int contestNumber=nodeContestsByUser.get("count").asInt();
                            ArrayList<ContestDTO> contestByUserDTOs=new ArrayList<>(contestNumber);
                            nodeContestsByUser.get("objects").forEach(contest ->{
                                log.info("contest for user " + contestUserDTO.getId() + ": " + contest.toString());
                                ContestDTO contestDTO=new ContestDTO(contest.get("id").asInt(), contest.get("name").asText(), contest.get("admin_id").asInt(),
                                                                    contest.get("start_datetime").asText(), contest.get("end_datetime").asText(), null);
                                contestByUserDTOs.add(contestDTO);
                            });

                            contestByUserDTOs.forEach((contestByUserDTO)->{
                                try{
                                    String stringSubmissionsByUserAndContest=new String(submissionClient.get_submissions_by_user_id_and_contest_id(contestUserDTO.getId(), contestByUserDTO.getId())
                                                                            .body().asInputStream().readAllBytes());
                                    JsonNode nodeSubmission=objectMapper.readTree(stringSubmissionsByUserAndContest).get("data");
                                    int submissionNumber=nodeSubmission.get("count").asInt();
                                    ArrayList<SubmissionDTO> submissionDTOs=new ArrayList<>(submissionNumber);
                                    nodeSubmission.get("objects").forEach(submission ->{
                                        log.info("submission given player and contest: " + submission.toString());
                                        SubmissionDTO submissionDTO=new SubmissionDTO(submission.get("id").asInt(), submission.get("challenge_id").asInt(), submission.get("contest_id").asInt(),
                                                                                    submission.get("user_id").asInt(), null, submission.get("submission_datetime").asText(), submission.get("submitted_flag").asText(), submission.get("solved").asBoolean());
                                        submissionDTOs.add(submissionDTO);
                                    });

                                    submissionDTOs.forEach((submissionDTO) ->{
                                        if(submissionDTO.isSolved()){
                                            try {
                                                String stringChallenge=new String(challengeClient.challenge_by_id(submissionDTO.getChallenge_id()).body().asInputStream().readAllBytes());
                                                JsonNode nodeChallenge=objectMapper.readTree(stringChallenge).get("data");
                                                int points=nodeChallenge.get("objects").get(0).get("points").asInt();
                                                int prevPoint=player.getValue();
                                                player.setValue(points+prevPoint);
                                            } catch (IOException e) {
                                                log.error("Error reading JSON in challenge client", e);
                                            }
                                        }
                                    });
                                }catch(IOException e){
                                    log.error("Error reading JSON body in submission client", e);
                                }
                            });
                        } catch (IOException e) {
                            log.error("Error reading JSON body in contest client looking for contest", e);
                        }
                        leaderboard.add(player);
                    });
                    leaderboard.sort(Comparator.comparing(Map.Entry::getValue));
                    response.setStatusCode(HttpStatus.OK);
                    String returnBody="{[";
                    for(int i=0; i<leaderboard.size(); i++){
                        returnBody+="{\"username\":\""+leaderboard.get(i).getKey() + "\",\"points\":\""+leaderboard.get(i).getValue()+"\"}";
                        if(i+1!=leaderboard.size()){
                            returnBody+=",";
                        }
                    }
                    returnBody+="]}";
                    DataBuffer buffer=response.bufferFactory().wrap(returnBody.getBytes());
                    response.getHeaders().add("Content-Type", "application/json");
                    return response.writeWith(Mono.just(buffer));
                } catch (IOException e) {
                    log.error("Error reading JSON body in contest client looking for users", e);
                }

            }
            return chain.filter(exchange);
        }

        if (!request.getHeaders().containsKey("Authorization")) {
            log.warn("No Authorization header found when required");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        final String token = request.getHeaders().getOrEmpty("Authorization").get(0);
        log.info("Token: " + token);
        String role="";
        try {
            String body =new String(userClient.getUserRole(token).body().asInputStream().readAllBytes());
            // ObjectMapper objectMapper=new ObjectMapper();
            JsonNode rootNode=objectMapper.readTree(body);
            role=rootNode.get("role").asText();
        } catch (IOException e) {
            log.error("Cannot take the role from the token");
        }
        log.info("role: " + role);
        if (role.isEmpty()) {
            log.info("User not found");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }else{
            if(!isTeacher){
                log.warn("Unmatched any route");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }else{
                if(!role.equals("ADMIN")){
                    if(!isStudent){
                        log.warn("Find a route for ADMIN but user is not an ADMIN");
                        response.setStatusCode(HttpStatus.FORBIDDEN);
                        return response.setComplete();
                    }else{
                        if(!role.equals("PLAYER")){
                            log.warn("Find a route for PLAYER but user is not a PLAYER");
                            response.setStatusCode(HttpStatus.FORBIDDEN);
                            return response.setComplete();
                        }else{
                            log.info("Find a route for a PLAYER");
                        }
                    }
                }else{
                    log.info("Find a route for an ADMIN");
                }
            }
        }

        if(uri.contains("/contests")){
            if(uri.equals("/api/contests") && request.getMethod().equals(HttpMethod.POST)){
                log.info("contests find");
                return DataBufferUtils.join(request.getBody()).flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String bodyString = new String(bytes, StandardCharsets.UTF_8);
                    log.info("body string: " + bodyString);
                    // ObjectMapper objectMapper = new ObjectMapper();
                    try {
                        JsonNode jsonNode = objectMapper.readTree(bodyString);
                        log.info("json node created");
                        String contest_name=jsonNode.get("name").asText();
                        log.info("contest name: "+ contest_name);
                        int id_admin=jsonNode.get("admin_id").asInt();
                        log.info("admin id: " + id_admin);
                        String start_datetime=jsonNode.get("start_datetime").asText();
                        log.info("start datetime: " + start_datetime);
                        String end_datetime=jsonNode.get("end_datetime").asText();
                        log.info("end datetime: " + end_datetime);
                        ArrayList<String> participants=new ArrayList<>();
                        log.info("participant: " + jsonNode.get("participants"));
                        jsonNode.get("participants").forEach(participant -> {
                            participant.forEach(p->participants.add(p.asText()));
                        });
                        participants.forEach(participant->log.info("participant: " + participant));
    
                        ContestDTO contestDTO=new ContestDTO();
                        contestDTO.setName(contest_name);
                        contestDTO.setAdmin_id(id_admin);
                        contestDTO.setStart_datetime(start_datetime);
                        contestDTO.setEnd_datetime(end_datetime);
                        contestDTO.setParticipants(participants);
                        String contestString=new String(contestClient.create_contest(contestDTO).body().asInputStream().readAllBytes());
                        JsonNode nodeContest=objectMapper.readTree(contestString);
                        JsonNode bodyContest=nodeContest.get("data").get("objects").get(0);
                        log.info("body contest: " + bodyContest);
                        log.info("id contest: " + bodyContest.get("id"));
                        ContestDTO newContestDTO=new ContestDTO(
                            bodyContest.get("id").asInt(), bodyContest.get("name").asText(), bodyContest.get("admin_id").asInt(),
                            bodyContest.get("start_datetime").asText(), bodyContest.get("end_datetime").asText(), participants
                        );
                        newContestDTO.getParticipants().forEach(participant -> log.info("participant: " + participant));
    
                        newContestDTO.getParticipants().forEach(participant -> {
                            ContestUserDTO contestUser=new ContestUserDTO();
                            contestUser.setUsername(participant);
                            contestClient.add_contestant(newContestDTO.getId(), contestUser);
                        });
                        response.setStatusCode(HttpStatus.CREATED);
                        return response.setComplete();
                    } catch (IOException e) {
                        log.error("Error reading JSON body", e);
                    }
                    return chain.filter(exchange);
                });
            }else{
                String[] parameters=uri.split("/");
                // log.info("uri length: " + parameters.length);
                // log.info("matching: " + parameters[3].matches("[0-9]+"));
                // log.info("uri starts with: " + uri.startsWith("/api/contests"));
                log.info("parameters length: " + parameters.length);
                if(parameters.length==5 && parameters[3].matches("[0-9]+")){
                    if(uri.startsWith("/api/contests") && request.getMethod().equals(HttpMethod.POST)){
                        int id_contest=Integer.parseInt(parameters[3]);
                        return DataBufferUtils.join(request.getBody()).flatMap(dataBuffer->{
                            byte[] bytes=new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            String bodyString=new String(bytes, StandardCharsets.UTF_8);
                            try {
                                JsonNode jsonNode=objectMapper.readTree(bodyString);
                                String email=jsonNode.get("username").asText();

                                String responseContest=new String(contestClient.get_contest(id_contest).body().asInputStream().readAllBytes());
                                JsonNode nodeContest=objectMapper.readTree(responseContest).get("data").get("objects").get(0);
                                log.info("contest: " + nodeContest);
                                log.info("participants: " + nodeContest.get("participants"));
                                log.info("participants[0]: " + nodeContest.get("participants").get(0));
                                String contest_name=nodeContest.get("name").asText();
                                log.info("contest name: "+ contest_name);
                                int id_admin=nodeContest.get("admin_id").asInt();
                                log.info("admin id: " + id_admin);
                                String start_datetime=nodeContest.get("start_datetime").asText();
                                log.info("start datetime: " + start_datetime);
                                String end_datetime=nodeContest.get("end_datetime").asText();
                                log.info("end datetime: " + end_datetime);
                                ArrayList<String> participants=new ArrayList<>();
                                nodeContest.get("participants").forEach(participant -> {
                                    participants.add(participant.get("username").asText());
                                });
                                participants.add(email);
                                participants.forEach(participant->log.info("participant: " + participant));
    
                                ContestDTO contestDTO=new ContestDTO();
                                contestDTO.setName(contest_name);
                                contestDTO.setAdmin_id(id_admin);
                                contestDTO.setStart_datetime(start_datetime);
                                contestDTO.setEnd_datetime(end_datetime);
                                contestDTO.setParticipants(participants);
                                String contestString=new String(contestClient.update_contest(id_contest, contestDTO).body().asInputStream().readAllBytes());
                                JsonNode nodeContestUpdate=objectMapper.readTree(contestString);
                                JsonNode bodyContest=nodeContestUpdate.get("data").get("objects").get(0);
                                log.info("body contest: " + bodyContest);
                                log.info("id contest: " + bodyContest.get("id"));
                                ContestDTO updatedContestDTO=new ContestDTO(
                                    bodyContest.get("id").asInt(), bodyContest.get("name").asText(), bodyContest.get("admin_id").asInt(),
                                    bodyContest.get("start_datetime").asText(), bodyContest.get("end_datetime").asText(), participants
                                );
                                
                                updatedContestDTO.getParticipants().forEach(participant ->{
                                    ContestUserDTO contestUserDTO=new ContestUserDTO();
                                    contestUserDTO.setUsername(participant);
                                    contestClient.add_contestant(id_contest, contestUserDTO);
                                });
                                response.setStatusCode(HttpStatus.CREATED);
                                return response.setComplete();
                            } catch (IOException e) {
                                log.error("Error reading JSON body", e);
                            }
                            return chain.filter(exchange);
                        });
                    }
                }else if(parameters.length==4 && parameters[3].matches("[0-9]+") && request.getMethod().equals(HttpMethod.POST)){
                    int id_contest=Integer.parseInt(parameters[3]);
                    return DataBufferUtils.join(request.getBody()).flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String bodyString = new String(bytes, StandardCharsets.UTF_8);
                        log.info("body string: " + bodyString);
                        // ObjectMapper objectMapper = new ObjectMapper();
                        try {
                            JsonNode jsonNode = objectMapper.readTree(bodyString);
                            log.info("json node created");
                            String contest_name=jsonNode.get("name").asText();
                            log.info("contest name: "+ contest_name);
                            int id_admin=jsonNode.get("admin_id").asInt();
                            log.info("admin id: " + id_admin);
                            String start_datetime=jsonNode.get("start_datetime").asText();
                            log.info("start datetime: " + start_datetime);
                            String end_datetime=jsonNode.get("end_datetime").asText();
                            log.info("end datetime: " + end_datetime);
                            ArrayList<String> participants=new ArrayList<>();
                            log.info("participant: " + jsonNode.get("participants"));
                            jsonNode.get("participants").forEach(participant -> {
                                participant.forEach(p->participants.add(p.asText()));
                            });
                            participants.forEach(participant->log.info("participant: " + participant));
        
                            ContestDTO contestDTO=new ContestDTO();
                            contestDTO.setName(contest_name);
                            contestDTO.setAdmin_id(id_admin);
                            contestDTO.setStart_datetime(start_datetime);
                            contestDTO.setEnd_datetime(end_datetime);
                            contestDTO.setParticipants(participants);
                            String contestString=new String(contestClient.update_contest(id_contest, contestDTO).body().asInputStream().readAllBytes());
                            JsonNode nodeContest=objectMapper.readTree(contestString);
                            JsonNode bodyContest=nodeContest.get("data").get("objects").get(0);
                            log.info("body contest: " + bodyContest);
                            log.info("id contest: " + bodyContest.get("id"));
                            ContestDTO newContestDTO=new ContestDTO(
                                bodyContest.get("id").asInt(), bodyContest.get("name").asText(), bodyContest.get("admin_id").asInt(),
                                bodyContest.get("start_datetime").asText(), bodyContest.get("end_datetime").asText(), participants
                            );
                            newContestDTO.getParticipants().forEach(participant -> log.info("participant: " + participant));
        
                            newContestDTO.getParticipants().forEach(participant -> {
                                ContestUserDTO contestUser=new ContestUserDTO();
                                contestUser.setUsername(participant);
                                contestClient.add_contestant(newContestDTO.getId(), contestUser);
                            });
                            response.setStatusCode(HttpStatus.CREATED);
                            return response.setComplete();
                        } catch (IOException e) {
                            log.error("Error reading JSON body", e);
                        }
                        return chain.filter(exchange);
                    });
                }
            }
        }else if(uri.contains("/statistics")){
            if(uri.equals("/api/statistics/university") || uri.equals("/api/statistics/gender") || uri.equals("/api/statistics/age")){
                ArrayList<Map.Entry<String, ArrayList<Integer>>> statistics = new ArrayList<>();
                ArrayList<Map.Entry<String, Integer>> users = new ArrayList<>();
                ArrayList<Map.Entry<String, ArrayList<Map.Entry<Integer, ArrayList<Map.Entry<Integer, Integer>>>>>> challengesCompleted=new ArrayList<>();
                ArrayList<Integer> ageIndex=new ArrayList<>();
                ageIndex.add(0, 0);
                switch (uri) {
                    case "/api/statistics/university" -> {
                        statistics.add(new AbstractMap.SimpleEntry<>("La Sapienza Università di Roma", new ArrayList<>()));
                        statistics.get(0).getValue().add(0, 0);
                        statistics.get(0).getValue().add(1, 0);
                        statistics.get(0).getValue().add(2, 0);
                        statistics.add(new AbstractMap.SimpleEntry<>("Università degli Studi di Roma Tor Vergata", new ArrayList<>()));
                        statistics.get(1).getValue().add(0, 0);
                        statistics.get(1).getValue().add(1, 0);
                        statistics.get(1).getValue().add(2, 0);
                        statistics.add(new AbstractMap.SimpleEntry<>("Università degli Studi Roma Tre", new ArrayList<>()));
                        statistics.get(2).getValue().add(0, 0);
                        statistics.get(2).getValue().add(1, 0);
                        statistics.get(2).getValue().add(2, 0);
                        users.add(new AbstractMap.SimpleEntry<>("La Sapienza Università di Roma", 0));
                        users.add(new AbstractMap.SimpleEntry<>("Università degli Studi di Roma Tor Vergata", 0));
                        users.add(new AbstractMap.SimpleEntry<>("Università degli Studi Roma Tre", 0));
                        challengesCompleted.add(new AbstractMap.SimpleEntry<>("La Sapienza Università di Roma", new ArrayList<>()));
                        challengesCompleted.add(new AbstractMap.SimpleEntry<>("Università degli Studi di Roma Tor Vergata", new ArrayList<>()));
                        challengesCompleted.add(new AbstractMap.SimpleEntry<>("Università degli Studi Roma Tre", new ArrayList<>()));
                        log.info("Statistics for university");
                        break;
                    }
                    case "/api/statistics/gender" -> {
                        statistics.add(new AbstractMap.SimpleEntry<>("Female", new ArrayList<>()));
                        statistics.get(0).getValue().add(0, 0);
                        statistics.get(0).getValue().add(1, 0);
                        statistics.get(0).getValue().add(2, 0);
                        statistics.add(new AbstractMap.SimpleEntry<>("Male", new ArrayList<>()));
                        statistics.get(1).getValue().add(0, 0);
                        statistics.get(1).getValue().add(1, 0);
                        statistics.get(1).getValue().add(2, 0);
                        users.add(new AbstractMap.SimpleEntry<>("Female", 0));
                        users.add(new AbstractMap.SimpleEntry<>("Male", 0));
                        challengesCompleted.add(new AbstractMap.SimpleEntry<>("Female", new ArrayList<>()));
                        challengesCompleted.add(new AbstractMap.SimpleEntry<>("Male", new ArrayList<>()));
                        log.info("Statistics for gendere");
                        break;
                    }
                    case "/api/statistics/age" -> {
                        log.info("Statistics for age");
                        break;
                    }
                    default -> {
                        log.warn("Unexpected error. Cannot instantiate data correctly");
                        break;
                    }
                }
                try {
                    String responseContestUser=new String(contestClient.get_contests_user().body().asInputStream().readAllBytes());
                    JsonNode nodeContestUser=objectMapper.readTree(responseContestUser).get("data");
                    int userNumber=nodeContestUser.get("count").asInt();
                    ArrayList<ContestUserDTO> contestUserDTOs=new ArrayList<>(userNumber);
                    nodeContestUser.get("objects").forEach(user->{
                        log.info("userContest: " + user);
                        ContestUserDTO contestUserDTO=new ContestUserDTO(user.get("id").asInt(), user.get("username").asText());
                        contestUserDTOs.add(contestUserDTO);
                    });
    
                    String responseUser=new String(userClient.get_user().body().asInputStream().readAllBytes());
                    JsonNode nodeUser=objectMapper.readTree(responseUser);
                    ArrayList<UserDTO> userDTOs=new ArrayList<>();
                    nodeUser.forEach(user->{
                        log.info("userAuth: " + user);
                        UserDTO userDTO=new UserDTO(user.get("matricola").asLong(), user.get("name").asText(), user.get("surname").asText(),
                                                    user.get("email").asText(), user.get("gender").asText(), user.get("dob").asText(),
                                                    user.get("password").asText(), user.get("university").asText(), user.get("role").asText());
                        userDTOs.add(userDTO);
                    });

                    userDTOs.forEach(user->{
                        boolean isUserContest=false;
                        ContestUserDTO contestUserDTO=new ContestUserDTO();
                        for(int i=0; i<userNumber && !isUserContest; i++){
                            isUserContest=isUserContest||user.getEmail().equals(contestUserDTOs.get(i).getUsername());
                            if(isUserContest){
                                contestUserDTO.setId(contestUserDTOs.get(i).getId());
                                contestUserDTO.setUsername(contestUserDTOs.get(i).getUsername());
                                log.info("find userContest for userAuth: " + contestUserDTO.getId() + " - " + contestUserDTO.getUsername());
                            }
                        }
                        if(isUserContest){
                            switch (uri) {
                                case "/api/statistics/university" -> {
                                    String university=user.getUniversity();
                                    switch (university){
                                        case "La Sapienza Università di Roma" -> {
                                            challengesCompleted.get(0).getValue().add(new AbstractMap.SimpleEntry<>(contestUserDTO.getId(), new ArrayList<>()));
                                            break;
                                        }
                                        case "Università degli Studi di Roma Tor Vergata" -> {
                                            challengesCompleted.get(1).getValue().add(new AbstractMap.SimpleEntry<>(contestUserDTO.getId(), new ArrayList<>()));
                                            break;
                                        }
                                        case "Università degli Studi Roma Tre" -> {
                                            challengesCompleted.get(2).getValue().add(new AbstractMap.SimpleEntry<>(contestUserDTO.getId(), new ArrayList<>()));
                                            break;
                                        }
                                        default -> {
                                            break;
                                        }
                                    }
                                    break;
                                }
                                case "/api/statistics/gender" -> {
                                    String gender=user.getGender();
                                    switch (gender){
                                        case "Female" -> {
                                            challengesCompleted.get(0).getValue().add(new AbstractMap.SimpleEntry<>(contestUserDTO.getId(), new ArrayList<>()));
                                            break;
                                        }
                                        case "Male" -> {
                                            challengesCompleted.get(1).getValue().add(new AbstractMap.SimpleEntry<>(contestUserDTO.getId(), new ArrayList<>()));
                                            break;
                                        }
                                        default -> {
                                            break;
                                        }
                                    }
                                    break;
                                }
                                case "/api/statistics/age" -> {
                                    int typesFind=statistics.size();
                                    String dob=user.getDob();
                                    String year=dob.split(" ")[3];
                                    if(typesFind==0){
                                        statistics.add(new AbstractMap.SimpleEntry<>(year, new ArrayList<>()));
                                        statistics.get(0).getValue().add(0, 0);
                                        statistics.get(0).getValue().add(1, 0);
                                        statistics.get(0).getValue().add(2, 0);
                                        users.add(new AbstractMap.SimpleEntry<>(year, 0));
                                        challengesCompleted.add(new AbstractMap.SimpleEntry<>(year, new ArrayList<>()));
                                        challengesCompleted.get(0).getValue().add(new AbstractMap.SimpleEntry<>(contestUserDTO.getId(), new ArrayList<>()));
                                    }else{
                                        boolean added=false;
                                        for(int i=0; i<typesFind; i++){
                                            String currentType=statistics.get(i).getKey();
                                            if(currentType.equals(year)){
                                                ageIndex.set(0, i);
                                                challengesCompleted.get(ageIndex.get(0)).getValue().add(new AbstractMap.SimpleEntry<>(contestUserDTO.getId(), new ArrayList<>()));
                                            }
                                        }
                                        if(!added){
                                            ageIndex.set(0, typesFind);
                                            statistics.add(new AbstractMap.SimpleEntry<>(year, new ArrayList<>()));
                                            statistics.get(ageIndex.get(0)).getValue().add(0,0);
                                            statistics.get(ageIndex.get(0)).getValue().add(1,0);
                                            statistics.get(ageIndex.get(0)).getValue().add(2,0);
                                            users.add(new AbstractMap.SimpleEntry<>(year, 0));
                                            challengesCompleted.add(new AbstractMap.SimpleEntry<>(year, new ArrayList<>()));
                                            challengesCompleted.get(ageIndex.get(0)).getValue().add(new AbstractMap.SimpleEntry<>(contestUserDTO.getId(), new ArrayList<>()));
                                        }
                                    }
                                    break;
                                }
                                default -> {
                                    log.warn("Unexpected error. Cannot instantiate data correctly");
                                    break;
                                }
                            }

                            try {
                                String stringContestsByUser=new String(contestClient.get_contests_by_user(contestUserDTO.getId()).body().asInputStream().readAllBytes());
                                JsonNode nodeContestsByUser=objectMapper.readTree(stringContestsByUser).get("data");
                                int contestNumber=nodeContestsByUser.get("count").asInt();
                                ArrayList<ContestDTO> contestByUserDTOs=new ArrayList<>(contestNumber);
                                nodeContestsByUser.get("objects").forEach(contest ->{
                                    log.info("contest for user " + contestUserDTO.getId() + ": " + contest);
                                    ContestDTO contestDTO=new ContestDTO(contest.get("id").asInt(), contest.get("name").asText(), contest.get("admin_id").asInt(),
                                                                        contest.get("start_datetime").asText(), contest.get("end_datetime").asText(), null);
                                    contestByUserDTOs.add(contestDTO);
                                });

                                contestByUserDTOs.forEach((contestByUserDTO)->{
                                    switch (uri) {
                                        case "/api/statistics/university" -> {
                                            String university=user.getUniversity();
                                            
                                            log.info("university: " + university);
                                            
                                            switch (university){
                                                case "La Sapienza Università di Roma" -> {
                                                    int currentUser=users.get(0).getValue();
                                                    log.info("currentUser Sapienza: " + currentUser);
                                                    challengesCompleted.get(0).getValue().get(currentUser).getValue().add(new AbstractMap.SimpleEntry<>(contestByUserDTO.getId(), 0));
                                                    break;
                                                }
                                                case "Università degli Studi di Roma Tor Vergata" -> {
                                                    int currentUser=users.get(1).getValue();
                                                    log.info("currentUser TorVergata: " + currentUser);
                                                    challengesCompleted.get(1).getValue().get(currentUser).getValue().add(new AbstractMap.SimpleEntry<>(contestByUserDTO.getId(), 0));
                                                    break;
                                                }
                                                case "Università degli Studi Roma Tre" -> {
                                                    int currentUser=users.get(2).getValue();
                                                    log.info("currentUser RomaTre: " + currentUser);
                                                    challengesCompleted.get(2).getValue().get(currentUser).getValue().add(new AbstractMap.SimpleEntry<>(contestByUserDTO.getId(), 0));
                                                    break;
                                                }
                                                default -> {
                                                    break;
                                                }
                                            }
                                            break;
                                        }
                                        case "/api/statistics/gender" -> {
                                            String gender=user.getGender();
                                            switch (gender){
                                                case "Female" -> {
                                                    int currentUser=users.get(0).getValue();
                                                    challengesCompleted.get(0).getValue().get(currentUser).getValue().add(new AbstractMap.SimpleEntry<>(contestByUserDTO.getId(), 0));
                                                    break;
                                                }
                                                case "Male" -> {
                                                    int currentUser=users.get(1).getValue();
                                                    challengesCompleted.get(1).getValue().get(currentUser).getValue().add(new AbstractMap.SimpleEntry<>(contestByUserDTO.getId(), 0));
                                                    break;
                                                }
                                                default -> {
                                                    break;
                                                }
                                            }
                                            break;
                                        }
                                        case "/api/statistics/age" -> {
                                            int currentUser=users.get(ageIndex.get(0)).getValue();
                                            challengesCompleted.get(ageIndex.get(0)).getValue().get(currentUser).getValue().add(new AbstractMap.SimpleEntry<>(contestByUserDTO.getId(), 0));
                                            break;
                                        }
                                        default -> {
                                            log.warn("Unexpected error. Cannot instantiate data correctly");
                                            break;
                                        }
                                    }

                                    try{
                                        String stringSubmissionsByUserAndContest=new String(submissionClient.get_submissions_by_user_id_and_contest_id(contestUserDTO.getId(), contestByUserDTO.getId())
                                                                                .body().asInputStream().readAllBytes());
                                        JsonNode nodeSubmission=objectMapper.readTree(stringSubmissionsByUserAndContest).get("data");
                                        int submissionNumber=nodeSubmission.get("count").asInt();
                                        ArrayList<SubmissionDTO> submissionDTOs=new ArrayList<>(submissionNumber);
                                        nodeSubmission.get("objects").forEach(submission ->{
                                            log.info("submission given player" + contestUserDTO.getId() + " and contest " + contestByUserDTO.getId() + " - " + submission);
                                            SubmissionDTO submissionDTO=new SubmissionDTO(submission.get("id").asInt(), submission.get("challenge_id").asInt(), submission.get("contest_id").asInt(),
                                                                                    submission.get("user_id").asInt(), null,submission.get("submission_datetime").asText(), submission.get("submitted_flag").asText(), submission.get("solved").asBoolean());
                                            submissionDTOs.add(submissionDTO);
                                        });

                                        submissionDTOs.forEach((submissionDTO) ->{
                                            switch (uri) {
                                                case "/api/statistics/university" -> {
                                                    String university=user.getUniversity();
                                                    switch (university){
                                                        case "La Sapienza Università di Roma" -> {
                                                            if(submissionDTO.isSolved()){
                                                                try {
                                                                    int numberChallenges=statistics.get(0).getValue().get(0);
                                                                    log.info("numberChallenges: " + numberChallenges);
                                                                    numberChallenges++;
                                                                    log.info("numberChallenges: " + numberChallenges);
                                                                    statistics.get(0).getValue().set(0, numberChallenges);

                                                                    String stringChallenge=new String(challengeClient.challenge_by_id(submissionDTO.getChallenge_id()).body().asInputStream().readAllBytes());
                                                                    JsonNode nodeChallenge=objectMapper.readTree(stringChallenge).get("data");
                                                                    int points=nodeChallenge.get("objects").get(0).get("points").asInt();
                                                                    int storedPoints=statistics.get(0).getValue().get(1);
                                                                    points+=storedPoints;
                                                                    statistics.get(0).getValue().set(1, points);

                                                                    int currentUser=users.get(0).getValue();
                                                                    challengesCompleted.get(0).getValue().get(currentUser).getValue().forEach(contest ->{
                                                                        if(contest.getKey()==contestByUserDTO.getId()){
                                                                            int challengeCompleted=contest.getValue();
                                                                            challengeCompleted++;
                                                                            contest.setValue(challengeCompleted);
                                                                        }
                                                                    });
                                                                } catch (IOException e) {
                                                                    log.error("Error reading JSON in challenge client", e);
                                                                }
                                                            }
                                                            break;
                                                        }
                                                        case "Università degli Studi di Roma Tor Vergata" -> {
                                                            if(submissionDTO.isSolved()){
                                                                try {
                                                                    int numberChallenges=statistics.get(1).getValue().get(0);
                                                                    numberChallenges++;
                                                                    statistics.get(1).getValue().set(0, numberChallenges);

                                                                    String stringChallenge=new String(challengeClient.challenge_by_id(submissionDTO.getChallenge_id()).body().asInputStream().readAllBytes());
                                                                    JsonNode nodeChallenge=objectMapper.readTree(stringChallenge).get("data");
                                                                    int points=nodeChallenge.get("objects").get(0).get("points").asInt();
                                                                    int storedPoints=statistics.get(1).getValue().get(1);
                                                                    points+=storedPoints;
                                                                    statistics.get(1).getValue().set(1, points);

                                                                    int currentUser=users.get(1).getValue();
                                                                    challengesCompleted.get(1).getValue().get(currentUser).getValue().forEach(contest ->{
                                                                        if(contest.getKey()==contestByUserDTO.getId()){
                                                                            int challengeCompleted=contest.getValue();
                                                                            challengeCompleted++;
                                                                            contest.setValue(challengeCompleted);
                                                                        }
                                                                    });
                                                                } catch (IOException e) {
                                                                    log.error("Error reading JSON in challenge client", e);
                                                                }
                                                            }
                                                            break;
                                                        }
                                                        case "Università degli Studi Roma Tre" -> {
                                                            if(submissionDTO.isSolved()){
                                                                try {
                                                                    int numberChallenges=statistics.get(2).getValue().get(0);
                                                                    numberChallenges++;
                                                                    statistics.get(2).getValue().set(0, numberChallenges);

                                                                    String stringChallenge=new String(challengeClient.challenge_by_id(submissionDTO.getChallenge_id()).body().asInputStream().readAllBytes());
                                                                    JsonNode nodeChallenge=objectMapper.readTree(stringChallenge).get("data");
                                                                    int points=nodeChallenge.get("objects").get(0).get("points").asInt();
                                                                    int storedPoints=statistics.get(2).getValue().get(1);
                                                                    points+=storedPoints;
                                                                    statistics.get(2).getValue().set(1, points);

                                                                    int currentUser=users.get(2).getValue();
                                                                    challengesCompleted.get(2).getValue().get(currentUser).getValue().forEach(contest ->{
                                                                        if(contest.getKey()==contestByUserDTO.getId()){
                                                                            int challengeCompleted=contest.getValue();
                                                                            challengeCompleted++;
                                                                            contest.setValue(challengeCompleted);
                                                                        }
                                                                    });
                                                                } catch (IOException e) {
                                                                    log.error("Error reading JSON in challenge client", e);
                                                                }
                                                            }
                                                            break;
                                                        }
                                                        default -> {
                                                            break;
                                                        }
                                                    }
                                                    break;
                                                }
                                                case "/api/statistics/gender" -> {
                                                    String gender=user.getGender();
                                                    switch (gender){
                                                        case "Female" -> {
                                                            if(submissionDTO.isSolved()){
                                                                try {
                                                                    int numberChallenges=statistics.get(0).getValue().get(0);
                                                                    numberChallenges++;
                                                                    statistics.get(0).getValue().set(0, numberChallenges);

                                                                    String stringChallenge=new String(challengeClient.challenge_by_id(submissionDTO.getChallenge_id()).body().asInputStream().readAllBytes());
                                                                    JsonNode nodeChallenge=objectMapper.readTree(stringChallenge).get("data");
                                                                    int points=nodeChallenge.get("objects").get(0).get("points").asInt();
                                                                    int storedPoints=statistics.get(0).getValue().get(1);
                                                                    points+=storedPoints;
                                                                    statistics.get(0).getValue().set(1, points);

                                                                    int currentUser=users.get(0).getValue();
                                                                    challengesCompleted.get(0).getValue().get(currentUser).getValue().forEach(contest ->{
                                                                        if(contest.getKey()==contestByUserDTO.getId()){
                                                                            int challengeCompleted=contest.getValue();
                                                                            challengeCompleted++;
                                                                            contest.setValue(challengeCompleted);
                                                                        }
                                                                    });
                                                                } catch (IOException e) {
                                                                    log.error("Error reading JSON in challenge client", e);
                                                                }
                                                            }
                                                            break;
                                                        }
                                                        case "Male" -> {
                                                            if(submissionDTO.isSolved()){
                                                                try {
                                                                    int numberChallenges=statistics.get(1).getValue().get(0);
                                                                    numberChallenges++;
                                                                    statistics.get(1).getValue().set(0, numberChallenges);

                                                                    String stringChallenge=new String(challengeClient.challenge_by_id(submissionDTO.getChallenge_id()).body().asInputStream().readAllBytes());
                                                                    JsonNode nodeChallenge=objectMapper.readTree(stringChallenge).get("data");
                                                                    int points=nodeChallenge.get("objects").get(0).get("points").asInt();
                                                                    int storedPoints=statistics.get(1).getValue().get(1);
                                                                    points+=storedPoints;
                                                                    statistics.get(1).getValue().set(1, points);

                                                                    int currentUser=users.get(1).getValue();
                                                                    challengesCompleted.get(1).getValue().get(currentUser).getValue().forEach(contest ->{
                                                                        if(contest.getKey()==contestByUserDTO.getId()){
                                                                            int challengeCompleted=contest.getValue();
                                                                            challengeCompleted++;
                                                                            contest.setValue(challengeCompleted);
                                                                        }
                                                                    });
                                                                } catch (IOException e) {
                                                                    log.error("Error reading JSON in challenge client", e);
                                                                }
                                                            }
                                                            break;
                                                        }
                                                        default -> {
                                                            break;
                                                        }
                                                    }
                                                    break;
                                                }
                                                case "/api/statistics/age" -> {
                                                    if(submissionDTO.isSolved()){
                                                        try {
                                                            int numberChallenges=statistics.get(ageIndex.get(0)).getValue().get(0);
                                                            numberChallenges++;
                                                            statistics.get(ageIndex.get(0)).getValue().set(0, numberChallenges);

                                                            String stringChallenge=new String(challengeClient.challenge_by_id(submissionDTO.getChallenge_id()).body().asInputStream().readAllBytes());
                                                            JsonNode nodeChallenge=objectMapper.readTree(stringChallenge).get("data");
                                                            int points=nodeChallenge.get("objects").get(0).get("points").asInt();
                                                            int storedPoints=statistics.get(ageIndex.get(0)).getValue().get(1);
                                                            points+=storedPoints;
                                                            statistics.get(ageIndex.get(0)).getValue().set(1, points);

                                                            int currentUser=users.get(ageIndex.get(0)).getValue();
                                                            challengesCompleted.get(ageIndex.get(0)).getValue().get(currentUser).getValue().forEach(contest ->{
                                                                if(contest.getKey()==contestByUserDTO.getId()){
                                                                    int challengeCompleted=contest.getValue();
                                                                    challengeCompleted++;
                                                                    contest.setValue(challengeCompleted);
                                                                }
                                                            });
                                                        } catch (IOException e) {
                                                            log.error("Error reading JSON in challenge client", e);
                                                        }
                                                    }
                                                    break;
                                                }
                                                default -> {
                                                    log.warn("Unexpected error. Cannot store data correctly");
                                                    break;
                                                }
                                            }
                                            log.info("end submission analysis of challenge " + submissionDTO.getChallenge_id() + " of contest " + contestByUserDTO.getId() + " of user " + user.getMatricola());
                                        });
                                    }catch(IOException e){
                                        log.error("Error reading JSON body in submission client", e);
                                    }
                                    log.info("end contest analysis " + contestByUserDTO.getId() + " of user " + user.getMatricola());
                                });
                            } catch (IOException e) {
                                log.error("Error reading JSON body in contest client looking for contest", e);
                            }
                            switch (uri) {
                                case "/api/statistics/university" -> {
                                    String university=user.getUniversity();
                                    switch (university){
                                        case "La Sapienza Università di Roma" -> {
                                            int numberUser=users.get(0).getValue();
                                            log.info("numberUser Sapienza: " + numberUser);
                                            numberUser++;
                                            log.info("numberUser Sapienza INCREMENTATO: " + numberUser);
                                            users.get(0).setValue(numberUser);
                                            break;
                                        }
                                        case "Università degli Studi di Roma Tor Vergata" -> {
                                            int numberUser=users.get(1).getValue();
                                            log.info("numberUser TorVergata: " + numberUser);
                                            numberUser++;
                                            log.info("numberUser TorVergata INCREMENTATO: " + numberUser);
                                            users.get(1).setValue(numberUser);
                                            break;
                                        }
                                        case "Università degli Studi Roma Tre" -> {
                                            int numberUser=users.get(2).getValue();
                                            log.info("numberUser RomaTre: " + numberUser);
                                            numberUser++;
                                            log.info("numberUser RomaTre INCREMENTATO: " + numberUser);
                                            users.get(2).setValue(numberUser);
                                            break;
                                        }
                                        default -> {
                                            break;
                                        }
                                    }
                                    break;
                                }
                                case "/api/statistics/gender" -> {
                                    String gender=user.getGender();
                                    switch (gender){
                                        case "Female" -> {
                                            int numberUser=users.get(0).getValue();
                                            numberUser++;
                                            users.get(0).setValue(numberUser);
                                            break;
                                        }
                                        case "Male" -> {
                                            int numberUser=users.get(1).getValue();
                                            numberUser++;
                                            users.get(1).setValue(numberUser);
                                            break;
                                        }
                                        default -> {
                                            break;
                                        }
                                    }
                                    break;
                                }
                                case "/api/statistics/age" -> {
                                    int numberUser=users.get(ageIndex.get(0)).getValue();
                                    numberUser++;
                                    users.get(ageIndex.get(0)).setValue(numberUser);
                                    break;
                                }
                                default -> {
                                    log.warn("Unexpected error. Cannot store data correctly");
                                    break;
                                }
                            }
                            log.info("end user analysis: " +user.getMatricola());
                        }
                    });
                } catch (IOException e) {
                    log.error("Error reading JSON body of contest", e);
                }

                try{
                    String responseCount=new String(challengeClient.count_contests().body().asInputStream().readAllBytes());
                    JsonNode nodeCount=objectMapper.readTree(responseCount);
                    JsonNode bodyCount=nodeCount.get("data");
                    ArrayList<CountContestDTO> countContestDTOs=new ArrayList<>();
                    bodyCount.get("objects").forEach(contest ->{
                        log.info("contest count: "+ contest);
                        CountContestDTO countContestDTO=new CountContestDTO(contest.get("contest_id").asInt(), contest.get("challenge_count").asInt());
                        countContestDTOs.add(countContestDTO);
                    });

                    challengesCompleted.forEach(type ->{
                        switch (uri) {
                            case "/api/statistics/university" -> {
                                String university=type.getKey();
                                switch (university){
                                    case "La Sapienza Università di Roma" -> {
                                        type.getValue().forEach(student ->{
                                            student.getValue().forEach(contest ->{
                                                countContestDTOs.forEach(countContest ->{
                                                    if(contest.getKey()==countContest.getContest_id()){
                                                        if(contest.getValue()==countContest.getChallenge_count()){
                                                            int counts=statistics.get(0).getValue().get(2);
                                                            counts++;
                                                            statistics.get(0).getValue().set(2, counts);
                                                        }
                                                    }
                                                });
                                            });
                                        });
                                        break;
                                    }
                                    case "Università degli Studi di Roma Tor Vergata" -> {
                                        type.getValue().forEach(student ->{
                                            student.getValue().forEach(contest ->{
                                                countContestDTOs.forEach(countContest ->{
                                                    if(contest.getKey()==countContest.getContest_id()){
                                                        if(contest.getValue()==countContest.getChallenge_count()){
                                                            int counts=statistics.get(1).getValue().get(2);
                                                            counts++;
                                                            statistics.get(1).getValue().set(2, counts);
                                                        }
                                                    }
                                                });
                                            });
                                        });
                                        break;
                                    }
                                    case "Università degli Studi Roma Tre" -> {
                                        type.getValue().forEach(student ->{
                                            student.getValue().forEach(contest ->{
                                                countContestDTOs.forEach(countContest ->{
                                                    if(contest.getKey()==countContest.getContest_id()){
                                                        if(contest.getValue()==countContest.getChallenge_count()){
                                                            int counts=statistics.get(2).getValue().get(2);
                                                            counts++;
                                                            statistics.get(2).getValue().set(2, counts);
                                                        }
                                                    }
                                                });
                                            });
                                        });
                                        break;
                                    }
                                    default -> {
                                        break;
                                    }
                                }
                                break;
                            }
                            case "/api/statistics/gender" -> {
                                String gender=type.getKey();
                                switch (gender){
                                    case "Female" -> {
                                        type.getValue().forEach(student ->{
                                            student.getValue().forEach(contest ->{
                                                countContestDTOs.forEach(countContest ->{
                                                    if(contest.getKey()==countContest.getContest_id()){
                                                        if(contest.getValue()==countContest.getChallenge_count()){
                                                            int counts=statistics.get(0).getValue().get(2);
                                                            counts++;
                                                            statistics.get(0).getValue().set(2, counts);
                                                        }
                                                    }
                                                });
                                            });
                                        });
                                        break;
                                    }
                                    case "Male" -> {
                                        type.getValue().forEach(student ->{
                                            student.getValue().forEach(contest ->{
                                                countContestDTOs.forEach(countContest ->{
                                                    if(contest.getKey()==countContest.getContest_id()){
                                                        if(contest.getValue()==countContest.getChallenge_count()){
                                                            int counts=statistics.get(0).getValue().get(2);
                                                            counts++;
                                                            statistics.get(0).getValue().set(2, counts);
                                                        }
                                                    }
                                                });
                                            });
                                        });
                                        break;
                                    }
                                    default -> {
                                        break;
                                    }
                                }
                            }
                            case "/api/statistics/age" -> {
                                type.getValue().forEach(student ->{
                                    student.getValue().forEach(contest ->{
                                        countContestDTOs.forEach(countContest ->{
                                            if(contest.getKey()==countContest.getContest_id()){
                                                if(contest.getValue()==countContest.getChallenge_count()){
                                                    int counts=statistics.get(ageIndex.get(0)).getValue().get(2);
                                                    counts++;
                                                    statistics.get(ageIndex.get(0)).getValue().set(2, counts);
                                                }
                                            }
                                        });
                                    });
                                });
                                break;
                            }
                            default -> {
                                log.warn("Unexpected error. Cannot store data correctly");
                                break;
                            }
                        }
                    });
                    
                    response.setStatusCode(HttpStatus.OK);
                    String returnBody="{[";
                    int typesNumber=statistics.size();
                    log.info("types number: " + typesNumber);
                    statistics.forEach(statistic ->{
                        log.info("type: " + statistic.getKey());
                        statistic.getValue().forEach(value ->{
                            log.info("value " + value);
                        });
                    });
                    for(int i=0; i<typesNumber; i++){
                        int userNumber=users.get(i).getValue();
                        double avgChallenge = (double) statistics.get(i).getValue().get(0) / userNumber;
                        double avgPoints= (double) statistics.get(i).getValue().get(1) / userNumber;
                        double avgContest=(double) statistics.get(i).getValue().get(2) / userNumber;
                        returnBody+="{\"filter\":\""+statistics.get(i).getKey() + "\",\"avg_challenges\":\""+avgChallenge+"\",\"avg_points\":\""+avgPoints+"\",\"avg_contests\":\""+avgContest+"\"}";
                        if(i+1!=typesNumber){
                            returnBody+=",";
                        }
                    }
                    returnBody+="]}";
                    DataBuffer buffer=response.bufferFactory().wrap(returnBody.getBytes());
                    response.getHeaders().add("Content-Type", "application/json");
                    return response.writeWith(Mono.just(buffer));
                }catch(IOException e){
                    log.error("Error on reading JSON body of challenge", e);
                }
            }else{
                log.warn("Received a wrong statistics uri");
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return response.setComplete();
            }
            
        }else if(uri.equals("/api/submissions") && request.getMethod().equals(HttpMethod.POST)){
            return DataBufferUtils.join(request.getBody()).flatMap(dataBuffer ->{
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer);
                String bodyString = new String(bytes, StandardCharsets.UTF_8);
                log.info("body string: " + bodyString);
                try{
                    JsonNode jsonNode=objectMapper.readTree(bodyString);
                    int challenge_id=jsonNode.get("challenge_id").asInt();
                    String flag_submitted=jsonNode.get("submitted_flag").asText();
                    String challengeString=new String(challengeClient.challenge_by_id(challenge_id).body().asInputStream().readAllBytes());
                    JsonNode challengeNode=objectMapper.readTree(challengeString).get("data").get("objects").get(0);
                    String rightFlag=challengeNode.get("flag").asText();
                    String email=jsonNode.get("user_mail").asText();
                    String responseContestUser=new String(contestClient.get_user(email).body().asInputStream().readAllBytes());
                    log.info("responseContestUser: " + responseContestUser);
                    JsonNode nodeContestUser=objectMapper.readTree(responseContestUser).get("data").get("objects").get(0);
                    int userNumber=nodeContestUser.get("id").asInt();
                    SubmissionDTO submissionDTO=new SubmissionDTO();
                    submissionDTO.setChallenge_id(challenge_id);
                    submissionDTO.setContest_id(jsonNode.get("contest_id").asInt());
                    submissionDTO.setUser_id(userNumber);
                    submissionDTO.setUser_email(email);
                    submissionDTO.setSubmitted_flag(flag_submitted);
                    if(flag_submitted.equals(rightFlag)){
                        submissionDTO.setSolved(true);
                        response.setStatusCode(HttpStatus.CREATED);
                    }else{
                        submissionDTO.setSolved(false);
                        response.setStatusCode(HttpStatus.OK);
                    }
                    submissionClient.create_submission(submissionDTO);
                }catch(IOException e){
                    log.warn("Error on reading JSON body over challenge", e);
                    response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                }
                return response.setComplete();
            });
        }else if (uri.contains("api/scoreboard")){
            String[] parameters=uri.split("/");
            if(parameters[3].matches("[0-9]+")){
                int contest_id=Integer.parseInt(parameters[3]);
                try {
                    String contestString=new String(contestClient.get_contest(contest_id).body().asInputStream().readAllBytes());
                    JsonNode contestNode=objectMapper.readTree(contestString).get("data").get("objects").get(0);
                    ArrayList<Map.Entry<String, Integer>> scoreboard=new ArrayList<>();
                    contestNode.get("participants").forEach(participant ->{
                        log.info("participant: " + participant);
                        String player=participant.get("username").asText();
                        int[] points={0};
                        try{
                            String userConteString=new String(contestClient.get_user(player).body().asInputStream().readAllBytes());
                            log.info(" ==> userConteString: " + userConteString);
                            JsonNode nodeContestUser=objectMapper.readTree(userConteString).get("data").get("objects").get(0);
                            int id_user = nodeContestUser.get("id").asInt();
                            String submissionString=new String(submissionClient.get_submissions_by_user_id_and_contest_id(id_user, contest_id).body().asInputStream().readAllBytes());
                            JsonNode submissionNode=objectMapper.readTree(submissionString).get("data");
                            submissionNode.get("objects").forEach(submission->{
                                log.info("submission: " + submission);
                                if(submission.get("solved").asBoolean()){
                                    try{
                                        String challengeString=new String(challengeClient.challenge_by_id(submission.get("challenge_id").asInt()).body().asInputStream().readAllBytes());
                                        JsonNode challengeNode=objectMapper.readTree(challengeString).get("data").get("objects").get(0);
                                        int userPoints=points[0];
                                        userPoints+=challengeNode.get("points").asInt();
                                        points[0]=userPoints;
                                    }catch(IOException e){
                                        log.error("Error reading JSON body of challenge for user", e);
                                    }
                                }
                            });
                        }catch(IOException e){
                            log.error("Error reading JSON body of contest for user", e);
                        }

                        scoreboard.add(new AbstractMap.SimpleEntry<>(player, points[0]));
                    });
                    scoreboard.sort(Comparator.comparing(Map.Entry::getValue));
                    response.setStatusCode(HttpStatus.OK);
                    String returnBody="{[";
                    int size=scoreboard.size();
                    for(int i=0; i<size; i++){
                        returnBody+="{\"username\":\""+scoreboard.get(i).getKey()+"\",\"points\":\""+scoreboard.get(i).getValue()+"\"}";
                        if(i+1!=size){
                            returnBody+=",";
                        }
                    }
                    returnBody+="]}";
                    DataBuffer buffer=response.bufferFactory().wrap(returnBody.getBytes());
                    response.getHeaders().add("Content-Type", "application/json");
                    return response.writeWith(Mono.just(buffer));
                } catch (IOException e) {
                    log.error("Connot read JSON body of contest", e);
                }
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}