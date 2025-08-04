package top.orosirian.model.Response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatResponseDTO {

    public Long chatId;

    public String title;

    public LocalDateTime updatedAt;

}
