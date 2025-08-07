package top.orosirian.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StringType {

    ALL_CHARACTER(0),
    NUMBER(1);

    private final int code;

}
