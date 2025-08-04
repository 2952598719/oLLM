package top.orosirian.utils.tools;

import groovy.util.logging.Slf4j;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import top.orosirian.utils.config.ThirdPartyConfig;

import java.util.Date;

@Slf4j
@Component
public class ThirdPartyTools {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyTools.class);
    @Autowired
    private ThirdPartyConfig thirdPartyConfig;

    @Autowired
    private JavaMailSender javaMailSender;

    public boolean sendEmail(String toEmail, String emailCode) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(thirdPartyConfig.getUsername());
            helper.setTo(toEmail);
            helper.setSubject(thirdPartyConfig.getEmailTitle());
            helper.setText(thirdPartyConfig.getEmailContent(), emailCode);
            helper.setSentDate(new Date());
            javaMailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("send email error", e);
            return false;
        }
    }



}
