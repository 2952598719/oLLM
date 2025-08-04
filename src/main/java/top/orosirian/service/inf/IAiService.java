package top.orosirian.service.inf;

import org.springframework.ai.chat.ChatResponse;
import reactor.core.publisher.Flux;
import top.orosirian.model.Response.ChatResponseDTO;
import top.orosirian.model.Response.MessageResponseDTO;

import java.util.List;

public interface IAiService {

    /**
     * 对话相关
     */
    List<ChatResponseDTO> getChatList(Long userId);

    void createChat(Long chatId, Long userId, String prefixString);

    void deleteChat(Long chatId, Long userId);

    /**
     * 消息相关
     */
    List<MessageResponseDTO> getMessageList(Long chatId, Long userId);

//    ChatResponse generate(String model, String message);

    Flux<ChatResponse> generateStream(Long userId, Long chatId, String model, String message);

    Flux<ChatResponse> generateStreamRag(Long userId, Long chatId, String ragTag, String model, String message);

}
