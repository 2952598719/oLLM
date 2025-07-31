package top.orosirian.entity.dto.user;

import lombok.Data;

@Data
public class RegisterDTO {

    String email;

    String password;

    String captcha;

    String verificationCode;

}
