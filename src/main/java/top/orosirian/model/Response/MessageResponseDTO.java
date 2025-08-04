package top.orosirian.model.Response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageResponseDTO {

    public Long messageId;

    public String role;

    public String content;

    public LocalDateTime createdAt;

}
