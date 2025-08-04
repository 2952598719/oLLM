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
import reactor.core.publisher.Flux;
import top.orosirian.model.Response.ChatResponseDTO;
import top.orosirian.mapper.OpenAiMapper;
import top.orosirian.model.Response.MessageResponseDTO;
import top.orosirian.service.inf.IAiService;
import top.orosirian.utils.Constant;

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
    public void deleteChat(Long chatId, Long userId) {
        if(!openAiMapper.isChatBelong(chatId, userId)) {
            return;
        }
        openAiMapper.deleteChat(chatId);
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

    @Override
    public Flux<ChatResponse> generateStream(Long userId, Long chatId, String model, String message) {
        if (chatId == null || !openAiMapper.isChatBelong(chatId, userId)) {
            return Flux.error(new IllegalArgumentException("无效的Chat ID或用户无权访问"));
        }
        // 1.保存用户消息
        Long userMessageId = snowflake.nextId();
        Long assistantMessageId = snowflake.nextId();
        openAiMapper.insertMessage(userMessageId, chatId, "user", message);
        // 2.获取历史消息
        List<MessageResponseDTO> historyMessages = openAiMapper.getMessageList(chatId);
        // 3.构造MessageList
        List<Message> messageList = new ArrayList<>();
        messageList.add(new SystemMessage(Constant.COMMON_PROMPT_TEMPLATE));
        historyMessages.forEach(msg -> {
            if ("user".equalsIgnoreCase(msg.getRole())) {
                messageList.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equalsIgnoreCase(msg.getRole())) {
                messageList.add(new AssistantMessage(msg.getContent()));
            }
        });
        // 4.发送请求，收集响应内容
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
