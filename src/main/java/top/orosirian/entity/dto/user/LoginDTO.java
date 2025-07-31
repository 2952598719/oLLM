package top.orosirian.entity.dto.user;

import lombok.Data;

@Data
public class LoginDTO {

    String email;

    String password;

    String captcha;

}
