package top.orosirian.service.inf;

import reactor.core.publisher.Mono;

public interface UserService {

    Mono<Boolean> isEmailExist(String email);

    Mono<Void> register(String email, String password);

    Mono<Long> login(String email, String password);

}
