package top.orosirian.model.annotation;

import top.orosirian.model.enums.VerificationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD})
public @interface VerifyAnnotation {
    /**
     * 通用
     */
    boolean required() default false;

    /**
     * 字符串
     */
    int min() default -1;

    int max() default -1;

    VerificationType regex() default VerificationType.NO;

}
