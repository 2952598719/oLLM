package top.orosirian.model.Response;

import lombok.Data;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Data
public class ChatResponseDTO {

    public String chatId;

    public String title;

    public Timestamp updatedAt;

}
