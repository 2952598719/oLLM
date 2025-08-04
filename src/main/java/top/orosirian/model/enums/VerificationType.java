package top.orosirian.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VerificationType {

    NO("", "不校验"),
    EMAIL("^\\s*\\w+(?:\\.{0,1}[\\w-]+)*@[a-zA-Z0-9]+(?:[-.][a-zA-Z0-9]+)*\\.[a-zA-Z]+\\s*$", "邮箱"),
    PASSWORD("^(?=.*[A-Za-z])(?=.*\\d).{8,}$", "密码");

    private final String regex;

    private final String desc;

}
