package top.orosirian.entity.dto.user;

import lombok.Data;

@Data
public class ResetPasswordDTO {

    String email;

    String newPassword;

    String verificationCode;

}
