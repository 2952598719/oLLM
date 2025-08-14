package top.orosirian.service.inf;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.orosirian.model.Response.ChatResponseDTO;
import top.orosirian.model.Response.MessageResponseDTO;

public interface IAiService {

    Mono<String> testConcurrent();

    /**
     * 对话相关
     */
    Flux<ChatResponseDTO> getChatList(Long userId);

    Mono<Void> createChat(Long chatId, Long userId, String prefixString);

    Mono<Boolean> deleteChat(Long chatId, Long userId);

    /**
     * 消息相关
     */
    Flux<MessageResponseDTO> getMessageList(Long chatId, Long userId);

//    ChatResponse generate(String model, String message);

    Flux<ChatResponse> generateStream(Long userId, Long chatId, String model, String message, boolean useTool, Long tagId);

}
