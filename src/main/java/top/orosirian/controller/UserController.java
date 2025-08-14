package top.orosirian.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiterReactive;
import org.redisson.api.RateType;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import top.orosirian.model.enums.StringType;
import top.orosirian.model.request.LoginRequest;
import top.orosirian.model.request.RegisterRequest;
import top.orosirian.service.inf.UserService;
import top.orosirian.utils.BusinessException;
import top.orosirian.utils.Captcha;
import top.orosirian.utils.Constant;
import top.orosirian.utils.tools.StringTools;
import top.orosirian.utils.tools.ThirdPartyTools;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    // 这里的校验注解简陋，不过知道原理比直接调库好，以后想用就可以从这参考

    @Autowired
    RedissonReactiveClient redissonReactiveClient;

    @Autowired
    UserService userService;

    @Autowired
    ThirdPartyTools thirdPartyTools;

    @GetMapping("/send_captcha")
    public Mono<ResponseEntity<byte[]>> sendCaptcha(ServerWebExchange exchange) {
        RRateLimiterReactive rateLimiter = redissonReactiveClient.getRateLimiter("captcha_rate_limiter");

        // 响应式限流
        return rateLimiter.trySetRate(RateType.OVERALL, 5, Duration.ofSeconds(1))
                .then(rateLimiter.tryAcquire())
                .flatMap(acquired -> {
                    if (!acquired) {
                        // 请求过于频繁
                        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build());
                    }

                    // 1. 生成验证码（CPU操作，可以不切换线程）
                    Captcha captcha = new Captcha(4);

                    // 2. 异步存入Redis
                    Mono<Void> saveToRedis = exchange.getSession()
                            .flatMap(session -> redissonReactiveClient
                                    .getBucket("captcha:" + session.getId())
                                    .set(captcha.getCode(), Duration.ofMinutes(10))
                            ).then();

                    // 3. 异步生成图片字节（ImageIO是阻塞的，需要切换线程）
                    Mono<byte[]> imageBytesMono = Mono.fromCallable(() -> {
                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            ImageIO.write(captcha.getBufferedImage(), "png", baos);
                            return baos.toByteArray();
                        }
                    }).publishOn(Schedulers.boundedElastic());

                    // 4. 并行执行Redis保存和图片生成，然后返回图片
                    return Mono.zip(saveToRedis, imageBytesMono)
                            .map(Tuple2::getT2) // 获取图片字节
                            .map(imageBytes -> ResponseEntity.ok()
                                    .contentType(MediaType.IMAGE_PNG)
                                    .header("Pragma", "no-cache")
                                    .header("Cache-Control", "no-cache")
                                    .body(imageBytes));
                });
    }

    @GetMapping("/send_email")
    public Mono<ResponseEntity<String>> sendEmail(@RequestParam String email, @RequestParam String type) {
        RRateLimiterReactive rateLimiter = redissonReactiveClient.getRateLimiter("email_rate_limiter:" + email);

        // 响应式限流和业务逻辑链
        return rateLimiter.trySetRate(RateType.OVERALL, 1, Duration.ofSeconds(60))
                .then(rateLimiter.tryAcquire())
                .flatMap(acquired -> {
                    if (!acquired) {
                        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("请求过于频繁"));
                    }
                    // 1. 响应式地检查邮箱是否存在
                    return userService.isEmailExist(email)
                            .flatMap(emailExists -> {
                                // 2. 根据类型进行校验
                                if ("register".equalsIgnoreCase(type) && emailExists) {
                                    return Mono.just(ResponseEntity.badRequest().body("该邮箱已注册"));
                                } else if ("reset".equalsIgnoreCase(type) && !emailExists) {
                                    return Mono.just(ResponseEntity.badRequest().body("该邮箱未注册"));
                                }

                                // 3. 校验通过，生成并发送邮件
                                String emailCode = StringTools.getRandomString(StringType.NUMBER, 6);
                                Mono<Void> saveToRedis = redissonReactiveClient.getBucket("email:" + email)
                                        .set(emailCode, Duration.ofMinutes(15)).then();

                                // 假设 thirdPartyTools.sendEmail 是阻塞的
                                Mono<Boolean> sendEmailMono = Mono.fromCallable(() -> thirdPartyTools.sendEmail(email, emailCode))
                                        .publishOn(Schedulers.boundedElastic());

                                // 4. 并行执行保存Redis和发送邮件
                                return Mono.zip(saveToRedis, sendEmailMono)
                                        .map(Tuple2::getT2) // 获取发送结果
                                        .map(sent -> sent
                                                ? ResponseEntity.ok().body("邮箱验证码已发送")
                                                : ResponseEntity.internalServerError().body("邮箱验证码发送失败"));
                            });
                });
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<String>> register(ServerWebExchange exchange,
            @Valid RegisterRequest request) {
        // 1. 首先检查邮箱是否已注册
        return userService.isEmailExist(request.getEmail())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.just(ResponseEntity.badRequest().body("该邮箱已被注册"));
                    }

                    // 2. 验证图形验证码
                    Mono<String> captchaFromRedis = exchange.getSession()
                            .flatMap(session -> redissonReactiveClient
                                    .<String>getBucket("captcha:" + session.getId())
                                    .getAndDelete());

                    return captchaFromRedis
                            .filter(storedCaptcha -> storedCaptcha.equalsIgnoreCase(request.getCaptcha()))
                            // 当 filter 为空时 (验证码不匹配或不存在)，发出一个 BusinessException 错误信号
                            .switchIfEmpty(Mono.defer(() ->
                                    Mono.error(new BusinessException(HttpStatus.BAD_REQUEST, "图形验证码错误或已过期"))
                            ))
                            .flatMap(storedCaptcha -> {
                                // 3. 验证邮箱验证码 (同样的处理方式)
                                Mono<String> codeFromRedis = redissonReactiveClient.<String>getBucket("email:" + request.getEmail()).getAndDelete();
                                return codeFromRedis
                                        .filter(storedCode -> storedCode.equals(request.getVerificationCode()))
                                        .switchIfEmpty(Mono.defer(() ->
                                                Mono.error(new BusinessException(HttpStatus.BAD_REQUEST, "邮箱验证码错误或已过期"))
                                        ));
                            })
                            .flatMap(storedCode ->
                                    // 4. 所有验证通过，执行注册
                                    userService.register(request.getEmail(), request.getPassword())
                                            .then(Mono.just(ResponseEntity.ok().body("注册成功")))
                            )
                            // 捕获我们之前抛出的错误信号，并将其转换为正常的HTTP响应
                            .onErrorResume(BusinessException.class, e -> Mono.just(e.toResponseEntity()))
                            // 捕获其他所有未预料的异常
                            .onErrorResume(e -> {
                                log.error("注册时发生未知错误", e); // 记录未知错误日志
                                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("服务器内部错误"));
                            });
                });
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<String>> login(ServerWebExchange exchange,
            @Valid LoginRequest request) {
        return userService.login(request.getEmail(), request.getPassword()) // 返回 Mono<Long> 或 Mono.empty()
                .flatMap(userId ->
                        // 登录成功，设置Session
                        exchange.getSession().doOnNext(session ->
                                session.getAttributes().put(Constant.USER_SESSION_KEY, userId)
                        ).thenReturn(ResponseEntity.ok().body("登陆成功"))
                )
                // 如果上游是 Mono.empty() (登录失败)，则执行这里
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("登陆失败，用户名或密码错误")));
    }

}
