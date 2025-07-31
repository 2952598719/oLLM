package top.orosirian.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class ThirdPartyUtils {

    @Autowired
    private JavaMailSender javaMailSender;



}
