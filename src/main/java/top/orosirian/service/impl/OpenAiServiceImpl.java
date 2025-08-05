package top.orosirian.service.impl;

import cn.hutool.core.lang.Snowflake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import top.orosirian.model.Response.ChatResponseDTO;
import top.orosirian.mapper.OpenAiMapper;
import top.orosirian.model.Response.MessageResponseDTO;
import top.orosirian.service.inf.IAiService;
import top.orosirian.utils.Constant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service("OpenAiServiceImpl")
public class OpenAiServiceImpl implements IAiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiServiceImpl.class);

    @Autowired
    private OpenAiChatClient chatClient;

    @Autowired
    private PgVectorStore pgVectorStore;

    @Autowired
    private OpenAiMapper openAiMapper;

    @Autowired
    private Snowflake snowflake;

    /**
     * 对话相关
     */
    @Override
    public List<ChatResponseDTO> getChatList(Long userId) {
        return openAiMapper.getChatList(userId);
    }

    @Override
    public void createChat(Long chatId, Long userId, String prefixString) {
        openAiMapper.createChat(chatId, userId, prefixString);
    }

    @Override
    public boolean deleteChat(Long chatId, Long userId) {
        if(!openAiMapper.isChatBelong(chatId, userId)) {
            log.info("对话不属于该用户");
            return false;
        } else {
            openAiMapper.deleteChat(chatId);
            openAiMapper.deleteMessage(chatId);
            return true;
        }

    }


    /**
     * 消息相关
     */
    @Override
    public List<MessageResponseDTO> getMessageList(Long chatId, Long userId) {
        if (!openAiMapper.isChatBelong(chatId, userId)) {
            return null;
        }
        return openAiMapper.getMessageList(chatId);
    }

//    @Override
//    public ChatResponse generate(String model, String message) {
//        return chatClient.call(new Prompt(message, OpenAiChatOptions.builder().withModel(model).build()));
//    }

    // 1. 方法签名：返回类型为 void，增加 SseEmitter 参数
    @Override
    public void generateStream(Long userId, Long chatId, String model, String message, SseEmitter emitter) {

        // 2. 修改验证逻辑：不再返回 Flux.error，而是直接抛出异常。
        //    这个异常将由 Controller 捕获，并用于通知 emitter 发生了错误。
        if (chatId == null || !openAiMapper.isChatBelong(chatId, userId)) {
            throw new IllegalArgumentException("无效的Chat ID或用户无权访问");
        }

        // --- 以下是您原有的业务逻辑，保持不变 ---
        // 保存用户消息
        Long userMessageId = snowflake.nextId();
        Long assistantMessageId = snowflake.nextId();
        openAiMapper.insertMessage(userMessageId, chatId, "user", message);

        // 获取历史消息
        List<MessageResponseDTO> historyMessages = openAiMapper.getMessageList(chatId);

        // 构造MessageList
        List<Message> messageList = new ArrayList<>();
        messageList.add(new SystemMessage(Constant.COMMON_PROMPT_TEMPLATE));
        historyMessages.forEach(msg -> {
            if ("user".equalsIgnoreCase(msg.getRole())) {
                messageList.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equalsIgnoreCase(msg.getRole())) {
                messageList.add(new AssistantMessage(msg.getContent()));
            }
        });

        // 发送请求，准备收集响应内容
        StringBuilder builder = new StringBuilder();
        Prompt prompt = new Prompt(messageList, OpenAiChatOptions.builder().withModel(model).build());

        // 3. 修改响应式流的处理方式
        chatClient.stream(prompt)
                .doOnNext(chatResponse -> {
                    try {
                        // 3a. 将每个数据块通过 emitter 发送给前端
                        emitter.send(chatResponse);

                        // 3b. (保留原有逻辑) 同时，累加内容以便最后存入数据库
                        String content = chatResponse.getResult().getOutput().getContent();
                        if (content != null) {
                            builder.append(content);
                        }
                    } catch (IOException e) {
                        // 如果发送失败 (通常是客户端断开了连接)，
                        // 抛出一个运行时异常，这会被下面的 doOnError 捕获。
                        log.warn("发送SSE事件时出错，客户端可能已断开连接: {}", e.getMessage());
                        throw new RuntimeException("SSE_SEND_ERROR", e);
                    }
                })
                .doOnComplete(() -> {
                    // 3c. (保留原有逻辑) 流结束后，保存完整的AI回复到数据库
                    String fullResponse = builder.toString();
                    if (!fullResponse.isEmpty()) {
                        openAiMapper.insertMessage(assistantMessageId, chatId, "assistant", fullResponse);
                        log.info("AI响应保存成功。ChatId: {}", chatId);
                    } else {
                        log.warn("流接收完成，但内容为空，不进行保存。ChatId: {}", chatId);
                    }
                    // 3d. (新增逻辑) 通知 emitter 数据流正常结束
                    emitter.complete();
                })
                .doOnError(throwable -> {
                    // 3e. (增强原有逻辑) 发生任何错误时
                    log.error("处理流时发生错误。ChatId: {}", chatId, throwable);
                    // 3f. (新增逻辑) 通知 emitter 发生了错误，这将关闭客户端的连接
                    emitter.completeWithError(throwable);
                })
                // 4. (关键步骤) 触发订阅
                //    因为我们现在是手动管理流，必须调用 subscribe() 来启动整个过程。
                .subscribe();
    }

    @Override
    public Flux<ChatResponse> generateStreamRag(Long userId, Long chatId, String ragTag, String model, String message) {
        if (chatId == null || !openAiMapper.isChatBelong(chatId, userId)) {
            return Flux.error(new IllegalArgumentException("无效的Chat ID或用户无权访问"));
        }
        Long userMessageId = snowflake.nextId();
        Long assistantMessageId = snowflake.nextId();
        // 1.保存用户消息
        openAiMapper.insertMessage(userMessageId, chatId, "user", message);
        // 2.获取历史消息
        List<MessageResponseDTO> historyMessages = openAiMapper.getMessageList(chatId);
        historyMessages = historyMessages.subList(0, historyMessages.size() - 1);
        // 3.获取rag消息
        SearchRequest request = SearchRequest.query(message)
                .withTopK(3)
                .withFilterExpression(String.format("knowledge == '%s'", ragTag));
        List<Document> documents = pgVectorStore.similaritySearch(request);
        String documentsContext = documents.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n---\n"));
        // 4.构造MessageList
        List<Message> messageList = new ArrayList<>();
        messageList.add(new SystemMessage(Constant.COMMON_PROMPT_TEMPLATE));
        historyMessages.forEach(msg -> {
            if (msg.getRole().equalsIgnoreCase("user")) {
                messageList.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole().equalsIgnoreCase("assistant")) {
                messageList.add(new AssistantMessage(msg.getContent()));
            }
        });
        messageList.add(new UserMessage(Constant.RAG_PROMPT_TEMPLATE
                .replace("{documents}", documentsContext)
                .replace("{question", message)));
        // 5.发送请求，收集响应内容
        StringBuilder builder = new StringBuilder();
        Prompt prompt = new Prompt(messageList, OpenAiChatOptions.builder().withModel(model).build());
        return chatClient.stream(prompt)
                .doOnNext(chatResponse -> {
                    String content = chatResponse.getResult().getOutput().getContent();
                    if (content != null) {
                        builder.append(content);
                    }
                })
                .doOnComplete(() -> {
                    String fullResponse = builder.toString();
                    if (!fullResponse.isEmpty()) {

                        openAiMapper.insertMessage(assistantMessageId, chatId, "assistant", fullResponse);
                        log.info("AI响应保存成功。");
                    } else {
                        log.warn("流接收完成，但内容为空，不进行保存。ChatId: {}", chatId);
                    }
                })
                .doOnError(throwable -> {
                    log.error("处理流时发生错误。ChatId: {}", chatId, throwable);
                });
    }

}
