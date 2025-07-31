package top.orosirian.service.impl.user;

import org.springframework.stereotype.Service;
import top.orosirian.service.inf.user.ThirdPartyService;

@Service
public class ThirdPartyServiceImpl implements ThirdPartyService {

//    @Autowired
//    private JavaMailSender javaMailSender;


    @Override
    public boolean sendEmail(String toEmail, String code) {
        return true;
    }

//    @Autowired
//    private JavaMailSender javaMailSender;

//    @Autowired
//    public boolean sendEmail(String toEmail, String code) {
//        return true;
//        try {
//            MimeMessage message = javaMailSender.createMimeMessage();
//            MimeMessageHelper helper = new MimeMessageHelper(message, true);
//            helper.setFrom(mailSender);
//            helper.setTo(email);
//
//            if ("register".equals(type)) {
//                helper.setSubject("注册验证码");
//                helper.setText("您的注册验证码是: " + emailCode + "，10分钟内有效");
//            } else {
//                helper.setSubject("密码重置验证码");
//                helper.setText("您的密码重置验证码是: " + emailCode + "，10分钟内有效");
//            }
//
//            helper.setSentDate(new Date());
//            javaMailSender.send(message);
//        } catch (Exception e) {
//            log.error("邮件发送失败", e);
//            return Response.<Boolean>builder()
//                    .code(HttpStatus.INTERNAL_SERVER_ERROR.getCode())
//                    .info("邮件发送失败")
//                    .data(false)
//                    .build();
//        }
//    }
}
