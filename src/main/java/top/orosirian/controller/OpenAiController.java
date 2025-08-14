package top.orosirian.controller;

import cn.hutool.core.lang.Snowflake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.orosirian.model.Response.ChatResponseDTO;
import top.orosirian.model.Response.MessageResponseDTO;
import top.orosirian.service.inf.IAiService;
import top.orosirian.utils.BusinessException;
import top.orosirian.utils.Constant;

import java.util.List;

@RestController
@RequestMapping("/api/v1/openai")
public class OpenAiController {

    private static final Logger log = LoggerFactory.getLogger(OpenAiController.class);

    @Autowired
    @Qualifier("OpenAiServiceImpl")
    private IAiService openAiService;

    @Autowired
    private Snowflake snowflake;

    @GetMapping("/testConcurrent")
    public Mono<ResponseEntity<String>> testConcurrent() {
        return openAiService.testConcurrent()
                .map(ResponseEntity::ok);
    }

    /**
     * 对话相关
     */
    // 获取对话列表
    // http://localhost:8090/api/v1/openai/chat_list
    @GetMapping("/chat_list")
    public Mono<ResponseEntity<List<ChatResponseDTO>>> chatList(ServerWebExchange exchange) {
        return exchange.getSession()
                .mapNotNull(session -> (Long) session.getAttribute(Constant.USER_SESSION_KEY))
                .flatMapMany(userId -> openAiService.getChatList(userId))
                .collectList()
                .map(ResponseEntity::ok);
    }

    // 创建对话
    // http://localhost:8090/api/v1/openai/create_chat?prefixString=xxx
    // prefixString为本次用户消息的前10个字
    @PostMapping("/create_chat")
    public Mono<ResponseEntity<Long>> createChat(ServerWebExchange exchange, @RequestParam String prefixString) {
        Long chatId = snowflake.nextId();
        return exchange.getSession()
                .mapNotNull(session -> (Long) session.getAttribute(Constant.USER_SESSION_KEY))
                .flatMap(userId -> openAiService.createChat(chatId, userId, prefixString))
                .then(Mono.just(ResponseEntity.ok(chatId)));
    }

    // 删除对话
    // http://localhost:8090/api/v1/openai/delete_chat?chatId=xxx
    @DeleteMapping("/delete_chat")
    public Mono<ResponseEntity<String>> deleteChat(ServerWebExchange exchange, @RequestParam Long chatId) {
        return exchange.getSession()
                .mapNotNull(session -> (Long) session.getAttribute(Constant.USER_SESSION_KEY))
                .flatMap(userId -> openAiService.deleteChat(chatId, userId))
                .map(deleted -> deleted
                        ? ResponseEntity.ok("删除成功")
                        : ResponseEntity.status(HttpStatus.FORBIDDEN).body("删除失败或无权限"));
    }

    /**
     * 消息相关
     */
    // 获取消息列表
    // http://localhost:8090/api/v1/openai/message_list?chatId=xxx
    @GetMapping("/message_list")
    public Mono<ResponseEntity<List<MessageResponseDTO>>> messageList(ServerWebExchange exchange, @RequestParam Long chatId) {
        return exchange.getSession()
                .mapNotNull(session -> (Long) session.getAttribute(Constant.USER_SESSION_KEY))
                .flatMapMany(userId -> openAiService.getMessageList(chatId, userId))
                .collectList()
                .map(ResponseEntity::ok);
    }


    // 发送并接收消息
    // http://localhost:8090/api/v1/ollama/generate_stream?chatId=xxx&model=deepseek-chat&message=xxx
    @GetMapping(value = "/generate_stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatResponse>> generateStream(ServerWebExchange exchange,
            @RequestParam String chatId, @RequestParam String model, @RequestParam String message,
            @RequestParam boolean useTool, @RequestParam(required = false) Long tagId) {

        return exchange.getSession()
                .mapNotNull(session -> (Long) session.getAttribute(Constant.USER_SESSION_KEY))
                .flatMapMany(userId ->
                        openAiService.generateStream(userId, Long.valueOf(chatId), model, message, useTool, tagId)
                                .map(chatResponse -> ServerSentEvent.builder(chatResponse).build())
                                .doOnSubscribe(subscription -> log.info("用户 {} 开始SSE流。ChatId: {}", userId, chatId))
                                .onErrorResume(e -> {
                                    log.error("SSE流处理时发生错误. ChatId: {}", chatId, e);
                                    String errorMessage = (e instanceof BusinessException) ? e.getMessage() : "流处理时发生内部错误";

                                    // 创建一个代表错误的 AssistantMessage
                                    AssistantMessage errorAssistantMessage = new AssistantMessage("Error: " + errorMessage);

                                    // 将其包装在 Generation 和 ChatResponse 中 (已修正语法)
                                    ChatResponse errorChatResponse = new ChatResponse(List.of(new Generation(errorAssistantMessage)));

                                    // 将这个类型正确的 ChatResponse 对象包装在 ServerSentEvent 中返回
                                    return Mono.just(
                                            ServerSentEvent.builder(errorChatResponse)
                                                    .event("error")
                                                    .build()
                                    );
                                })
                );
    }

}
