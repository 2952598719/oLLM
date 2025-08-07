package top.orosirian.model.Response;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class MessageResponseDTO {

    public String messageId;

    public String role;

    public String content;

    public Timestamp createdAt;

}
