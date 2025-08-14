package top.orosirian.utils;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@EqualsAndHashCode(callSuper = true)
@Data
public class BusinessException extends RuntimeException {

    private HttpStatus httpStatus;

    private String message;

    public BusinessException(HttpStatus status) {
        super(status.getReasonPhrase());
        this.httpStatus = status;
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.httpStatus = status;
        this.message = message;
    }

    public ResponseEntity<String> toResponseEntity() {
        return ResponseEntity.status(this.httpStatus).body(this.message);
    }

}
