package top.orosirian.controller;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.orosirian.model.annotation.InterceptorAnnotation;
import top.orosirian.model.annotation.VerifyAnnotation;
import top.orosirian.model.enums.StringType;
import top.orosirian.model.enums.VerificationType;
import top.orosirian.service.inf.UserService;
import top.orosirian.utils.Captcha;
import top.orosirian.utils.Constant;
import top.orosirian.utils.tools.StringTools;
import top.orosirian.utils.tools.ThirdPartyTools;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    // 这里的校验注解简陋，不过知道原理比直接调库好，以后想用就可以从这参考

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    UserService userService;

    @Autowired
    ThirdPartyTools thirdPartyTools;

    private static final RateLimiter captchaRateLimiter = RateLimiter.create(5.0);

    private static final RateLimiter emailRateLimiter = RateLimiter.create(1.0 / 60);

    @GetMapping("/send_captcha")
    public void sendCaptcha(HttpSession session, HttpServletResponse response) throws IOException {
        if (!captchaRateLimiter.tryAcquire()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("请求过于频繁");
            return;
        }
        // 1.获取验证码
        Captcha captcha = new Captcha(4);
        // 2.放到redis
        RBucket<String> bucket = redissonClient.getBucket("captcha:" + session.getId());
        bucket.set(captcha.getCode(), Duration.ofMinutes(10));    // 有效期10min
        // 3.通过response发送
        response.setHeader("Pragma", "no-cache");   // 兼容旧浏览器
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setContentType("image/png");
        try (OutputStream os = response.getOutputStream()) {    // 自动关闭
            ImageIO.write(captcha.getBufferedImage(), "png", os);
        }
    }

    // 限流
    @InterceptorAnnotation(requireVerify = true)
    @GetMapping("/send_email")
    public ResponseEntity<String> sendEmail(@RequestParam @VerifyAnnotation(regex = VerificationType.EMAIL) String email,
                                    @RequestParam String type) {
        if (!emailRateLimiter.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("请求过于频繁");
        }
        // 根据操作类型校验邮箱状态
        boolean emailExists = userService.isEmailExist(email);
        if ("register".equalsIgnoreCase(type) && emailExists) {
            return ResponseEntity.badRequest().body("该邮箱已注册");
        } else if ("reset".equalsIgnoreCase(type) && !emailExists) {
            return ResponseEntity.badRequest().body("该邮箱未注册");
        }
        String emailCode = StringTools.getRandomString(StringType.NUMBER, 6);
        RBucket<String> bucket = redissonClient.getBucket("email:" + email);
        bucket.set(emailCode, Duration.ofMinutes(15));
        if (thirdPartyTools.sendEmail(email, emailCode)) {
            return ResponseEntity.ok().body("邮箱验证码已发送");
        } else {
            return ResponseEntity.internalServerError().body("邮箱验证码发送失败");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(HttpSession session,
                                      @RequestParam @VerifyAnnotation(regex = VerificationType.EMAIL) String email,
                                      @RequestParam @VerifyAnnotation(regex = VerificationType.PASSWORD) String password,
                                      @RequestParam String captcha,
                                      @RequestParam String verificationCode) {
        // 邮箱重复性检查
        if (userService.isEmailExist(email)) {
            return ResponseEntity.badRequest().body("该邮箱已被注册");
        }

        // 图形验证码
        RBucket<String> captchaBucket = redissonClient.getBucket("captcha:" + session.getId());
        String storedCaptcha = captchaBucket.getAndDelete();    // 验证码一次性使用
        if (storedCaptcha == null || !storedCaptcha.equalsIgnoreCase(captcha)) {
            return ResponseEntity.badRequest().body("图形验证码错误或已过期");
        }

        // 邮箱验证码
        RBucket<String> codeBucket = redissonClient.getBucket("email:" + email);
        String storedVerificationCode = codeBucket.getAndDelete();
        if (storedVerificationCode == null || !storedVerificationCode.equals(verificationCode)) {
            return ResponseEntity.badRequest().body("邮箱验证码错误或已过期");
        }

        // 插入数据库
        userService.register(email, password);
        log.info("注册成功");
        return ResponseEntity.ok().body("注册成功");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(HttpSession session,
                                   @RequestParam @VerifyAnnotation(regex = VerificationType.EMAIL) String email,
                                   @RequestParam @VerifyAnnotation(regex = VerificationType.PASSWORD) String password) {
        Long userId = userService.login(email, password);
        if (userId != -1) {
            session.setAttribute(Constant.USER_SESSION_KEY, userId);
            log.info("登陆成功");
            return ResponseEntity.ok().body("登陆成功");
        } else {
            log.info("登陆失败");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("登陆失败，用户名或密码错误");
        }
    }

//    @PostMapping("/quit")
//    @InterceptorAnnotation(requireLogin = true)
//    public ResponseEntity<String> quit(HttpSession session) {
//
//    }

}
