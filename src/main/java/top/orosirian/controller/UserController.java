package top.orosirian.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.orosirian.entity.Response;
import top.orosirian.entity.dto.user.LoginDTO;
import top.orosirian.entity.dto.user.RegisterDTO;
import top.orosirian.entity.dto.user.ResetPasswordDTO;
import top.orosirian.entity.enums.HttpStatus;
import top.orosirian.entity.enums.StringType;
import top.orosirian.service.inf.user.UserService;
import top.orosirian.utils.Captcha;
import top.orosirian.utils.StringUtils;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.time.Duration;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/user")
public class UserController {

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    UserService userService;

    @GetMapping("/send_captcha")
    public Response<Boolean> sendCaptcha(HttpSession session, HttpServletResponse response) throws IOException {
        // 1.获取验证码
        Captcha captcha = new Captcha(4);
        // 2.放到redis
        RBucket<String> bucket = redissonClient.getBucket("captcha:" + session.getId());
        bucket.set(captcha.getCode(), Duration.ofMinutes(10));    // 有效期10min
        // 3.通过response发送
        response.setHeader("Pragma", "no-cache");   // 兼容旧浏览器
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setContentType("image/jpeg");
        ImageIO.write(captcha.getBufferedImage(), "png", response.getOutputStream());
        response.getOutputStream().close();
        return Response.<Boolean>builder()
                .code(HttpStatus.OK.getCode())
                .info("验证码发送成功")
                .data(Boolean.TRUE)
                .build();
    }

    @GetMapping("/send_email")
    public Response<Boolean> sendEmail(HttpSession session,
                                       @RequestParam String email,
                                       @RequestParam String type,
                                       @RequestParam String captcha) {
        // 1. 验证图形验证码
        RBucket<String> captchaBucket = redissonClient.getBucket("captcha:" + session.getId());
        String storedCaptcha = captchaBucket.getAndDelete();    // 验证码一次性使用
        if (storedCaptcha == null || !storedCaptcha.equals(captcha)) {
            return Response.<Boolean>builder()
                    .code(HttpStatus.BAD_REQUEST.getCode())
                    .info("图形验证码错误或已过期")
                    .data(false)
                    .build();
        }
        // 2. 检查邮箱格式
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            return Response.<Boolean>builder()
                    .code(HttpStatus.BAD_REQUEST.getCode())
                    .info("邮箱格式不正确")
                    .data(false)
                    .build();
        }
        // 3. 根据操作类型校验邮箱状态
        boolean emailExists = userService.isEmailExist(email);
        if ("register".equalsIgnoreCase(type) && emailExists) {
            return Response.<Boolean>builder()
                    .code(HttpStatus.BAD_REQUEST.getCode())
                    .info("该邮箱已被注册")
                    .data(false)
                    .build();
        } else if ("reset".equalsIgnoreCase(type) && !emailExists) {
            return Response.<Boolean>builder()
                    .code(HttpStatus.BAD_REQUEST.getCode())
                    .info("该邮箱未注册")
                    .data(false)
                    .build();
        }
        // 4. 生成并发送邮箱验证码
        String emailCode = StringUtils.getRandomString(StringType.NUMBER, 6);
        return null;

    }

    @PostMapping("/register")
    public Response<Boolean> register(HttpSession session, @RequestBody RegisterDTO user) {
        // （前端获取验证码、校验码）
        // 判断captcha
        // 判断校验码
        // 插入数据库，记得给password加密md5
        return null;
    }

    @PostMapping("/login")
    public Response<Boolean> login(HttpSession session, @RequestBody LoginDTO user) {
        // （前端获取验证码）
        // 判断captcha
        // 根据user从数据库里取，判断是否正确
        // 如果正确则将userId放入session
        return null;
    }

    @PostMapping("/reset_password")
    public Response<Boolean> resetPassword(HttpSession session, @RequestBody ResetPasswordDTO user) {
        // （前端获取验证码、校验码）
        // 判断captcha
        // 判断校验码
        // 修改密码
        return null;
    }


}
