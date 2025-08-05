package top.orosirian.mapper;

import org.apache.ibatis.annotations.Mapper;
import top.orosirian.model.Response.ChatResponseDTO;
import top.orosirian.model.Response.MessageResponseDTO;

import java.util.List;

@Mapper
public interface OpenAiMapper {

    List<ChatResponseDTO> getChatList(Long userId);

    void createChat(Long chatId, Long userId, String title);

    boolean isChatBelong(Long chatId, Long userId);

    void deleteChat(Long chatId);

    void deleteMessage(Long chatId);

    List<MessageResponseDTO> getMessageList(Long chatId);

    void insertMessage(Long messageId, Long chatId, String role, String content);

    void insertMessageWithRag(Long messageId, Long chatId, Long tagId, String role, String content);


}
