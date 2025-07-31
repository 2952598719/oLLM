package top.orosirian.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HttpStatus {

    OK(200),
    BAD_REQUEST(400),
    NOT_FOUND(404),
    CONFLICT(409),
    ;

    private final int code;

}
