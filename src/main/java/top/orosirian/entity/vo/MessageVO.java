package top.orosirian.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {

    public Long messageId;

    public String role;

    public String content;

    public LocalDateTime createdAt;

}
