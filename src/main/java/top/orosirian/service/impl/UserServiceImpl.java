package top.orosirian.service.impl;

import java.util.Objects;

import cn.hutool.core.lang.Snowflake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.orosirian.mapper.UserMapper;
import top.orosirian.service.inf.UserService;
import top.orosirian.utils.tools.StringTools;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private Snowflake snowflake;

    @Autowired
    private UserMapper userMapper;

    @Override
    public Mono<Boolean> isEmailExist(String email) {
        return Mono.fromCallable(() -> userMapper.isEmailExist(email))
                .publishOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> register(String email, String password) {
        // 先检查邮箱是否存在，构建响应式链
        return isEmailExist(email)
                .flatMap(exists -> {
                    if (exists) {
                        // 如果已存在，则什么都不做，直接完成
                        return Mono.empty();
                    }
                    // 如果不存在，则执行注册的阻塞逻辑
                    return Mono.fromRunnable(() -> {
                                Long userId = snowflake.nextId();
                                String encryptPassword = StringTools.encrypt(password);
                                userMapper.register(userId, email, encryptPassword);
                            })
                            .publishOn(Schedulers.boundedElastic());
                })
                .then(); // 最终转换为 Mono<Void>
    }

    @Override
    public Mono<Long> login(String email, String password) {
        return Mono.fromCallable(() -> {
                    String realPassword = userMapper.getPassword(email);
                    if (realPassword != null && StringTools.matches(password, realPassword)) {
                        return userMapper.getUserId(email);
                    }
                    return null; // 登录失败返回 null
                })
                .publishOn(Schedulers.boundedElastic())
                .filter(Objects::nonNull); // 如果为null，filter会将其转换为空的Mono (Mono.empty())
    }

}
