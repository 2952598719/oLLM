package top.orosirian.utils.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Data
@Configuration
public class ThirdPartyConfig {

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private Integer port;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    @Value("${spring.mail.default-encoding}")
    private String defaultEncoding;

    @Value("${spring.mail.properties.mail.debug}")
    private Boolean debug;

    @Value("${spring.mail.properties.mail.smtp.socketFactory.class}")
    private String socketFactoryClass;

    private String emailTitle = "邮箱验证码";

    private String emailContent = "你好，您的邮箱验证码是：%s，15分钟有效";

    @Bean
    public JavaMailSenderImpl JavaMailSender() {
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost(host);
        javaMailSender.setPort(port);
        javaMailSender.setProtocol(JavaMailSenderImpl.DEFAULT_PROTOCOL);
        javaMailSender.setUsername(username);
        javaMailSender.setPassword(password);
        javaMailSender.setDefaultEncoding(defaultEncoding);
        Properties properties = new Properties();
        // 必要的主配置项（QQ邮箱强制要求）
        properties.put("mail.smtp.auth", true);
        properties.put("mail.smtp.ssl.enable", true);
        // 根据yaml配置添加的属性
        properties.put("mail.smtp.socketFactory.class", socketFactoryClass);
        properties.put("mail.debug", debug);  // 启用调试模式
        javaMailSender.setJavaMailProperties(properties);
        return javaMailSender;
    }


}
