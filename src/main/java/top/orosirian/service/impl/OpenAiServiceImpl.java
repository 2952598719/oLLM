package top.orosirian.service.impl;

import cn.hutool.core.lang.Snowflake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
    private OpenAiChatModel openAiChatModel;

    @Autowired
    private PgVectorStore pgVectorStore;

    @Autowired
    private OpenAiMapper openAiMapper;

    @Autowired
    private Snowflake snowflake;

    @Autowired
    private ToolCallbackProvider tools;

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

    @Override
    public void generateStream(Long userId, Long chatId, String model, String message, SseEmitter emitter, boolean useTool, Long tagId) {
        // 0.验证消息格式
        if (chatId == null || !openAiMapper.isChatBelong(chatId, userId)) {
            throw new IllegalArgumentException("无效的Chat ID或用户无权访问");
        }
        // 1.保存用户消息
        Long userMessageId = snowflake.nextId();
        Long assistantMessageId = snowflake.nextId();
        openAiMapper.insertMessage(userMessageId, chatId, "user", message);
        // 2.构造上下文消息列表
        List<MessageResponseDTO> historyMessages = openAiMapper.getMessageList(chatId);
        historyMessages = historyMessages.subList(0, historyMessages.size() - 1);
        List<Message> messageList = new ArrayList<>();
        if (!useTool) {
            messageList.add(new SystemMessage(Constant.COMMON_PROMPT_TEMPLATE));
        } else {
            messageList.add(new SystemMessage(Constant.TOOL_PROMPT_TEMPLATE_MAC));
        }
        historyMessages.forEach(msg -> {
            if ("user".equalsIgnoreCase(msg.getRole())) {
                messageList.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equalsIgnoreCase(msg.getRole())) {
                messageList.add(new AssistantMessage(msg.getContent()));
            }
        });
        // 3.构造当前消息
        if (tagId == null) {
            messageList.add(new UserMessage(message));
        } else {
            SearchRequest request = SearchRequest.builder()
                    .query(message)
                    .topK(3)
                    .filterExpression(String.format("knowledge == '%s'", tagId))
                    .build();
            List<Document> documents = pgVectorStore.similaritySearch(request);
            String documentsContext;
            if (documents != null) {
                documentsContext = documents.stream()
                        .map(Document::getFormattedContent)
                        .collect(Collectors.joining("\n---\n"));
            } else {
                documentsContext = "未检索到消息";
            }
            messageList.add(new UserMessage(Constant.RAG_PROMPT_TEMPLATE
                    .replace("{documents}", documentsContext)
                    .replace("{question}", message)));
        }
        // 4.构造提示词
        Prompt prompt;
        if (!useTool) {
            prompt = new Prompt(messageList, OpenAiChatOptions.builder().model(model).build());
        } else {
            ToolCallback[] toolCallbacks = (ToolCallback[]) tools.getToolCallbacks();
            ChatOptions chatOptions = ToolCallingChatOptions.builder()
                    .model(model)
                    .toolCallbacks(toolCallbacks)
                    .build();
            prompt = new Prompt(messageList, chatOptions);
        }
        // 5.发送请求，收集响应
        StringBuilder builder = new StringBuilder();
        openAiChatModel.stream(prompt)
                .doOnNext(chatResponse -> {
                    try {
                        emitter.send(chatResponse);     // 通过emitter将每个数据块发给前端
                        String content = chatResponse.getResult().getOutput().getText();
                        if (content != null) {
                            builder.append(content);
                        }
                    } catch (IOException e) {
                        log.warn("发送SSE事件时出错，客户端可能已断开连接: {}", e.getMessage());
                        throw new RuntimeException("SSE_SEND_ERROR", e);
                    }
                })
                .doOnComplete(() -> {
                    String fullResponse = builder.toString();
                    if (!fullResponse.isEmpty()) {
                        openAiMapper.insertMessage(assistantMessageId, chatId, "assistant", fullResponse);
                        log.info("AI响应保存成功。ChatId: {}", chatId);
                    } else {
                        log.warn("流接收完成，但内容为空，不进行保存。ChatId: {}", chatId);
                    }
                    emitter.complete();     // 通知 emitter 数据流正常结束
                })
                .doOnError(throwable -> {
                    log.error("处理流时发生错误。ChatId: {}", chatId);
                    emitter.completeWithError(throwable);   // 通知 emitter 发生了错误，这将关闭客户端的连接
                })
                .subscribe();   // 调用 subscribe() 来启动整个过程
    }

}
