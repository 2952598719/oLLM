package top.orosirian.service.impl;

import cn.hutool.core.lang.Snowflake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.orosirian.model.Response.ChatResponseDTO;
import top.orosirian.mapper.OpenAiMapper;
import top.orosirian.model.Response.MessageResponseDTO;
import top.orosirian.service.inf.IAiService;
import top.orosirian.utils.BusinessException;
import top.orosirian.utils.Constant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    @Autowired
    @Qualifier("toolExecutor")
    private TaskExecutor toolExecutor;

    private final Map<Long, Mono<List<ChatResponseDTO>>> chatListCache = new ConcurrentHashMap<>();

    private final Map<Long, Mono<List<MessageResponseDTO>>> messageListCache = new ConcurrentHashMap<>();

    @Override
    public Mono<String> testConcurrent() {
        return Mono.fromCallable(() -> openAiMapper.testConcurrent())
                .publishOn(Schedulers.boundedElastic());
    }

    /**
     * 对话相关
     */
    @Override
    public Flux<ChatResponseDTO> getChatList(Long userId) {
        // 使用 computeIfAbsent 实现响应式缓存
        return chatListCache.computeIfAbsent(userId, id ->
                        Mono.fromCallable(() -> openAiMapper.getChatList(id))
                                .publishOn(Schedulers.boundedElastic())
                                .cache(Duration.ofMinutes(10)) // 缓存结果10分钟
                )
                .flatMapMany(Flux::fromIterable); // 将缓存的 Mono<List<T>> 展开为 Flux<T>
    }

    @Override
    public Mono<Void> createChat(Long chatId, Long userId, String prefixString) {
        return Mono.fromRunnable(() -> {
                    openAiMapper.createChat(chatId, userId, prefixString);
                    // 【缓存改造】手动使缓存失效
                    chatListCache.remove(userId);
                })
                .publishOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Boolean> deleteChat(Long chatId, Long userId) {
        return Mono.fromCallable(() -> openAiMapper.isChatBelong(chatId, userId))
                .publishOn(Schedulers.boundedElastic())
                .flatMap(isBelong -> {
                    if (!isBelong) {
                        log.info("对话不属于该用户");
                        return Mono.just(false);
                    }
                    return Mono.fromRunnable(() -> {
                                openAiMapper.deleteChat(chatId);
                                openAiMapper.deleteMessage(chatId);
                                // 【缓存改造】手动使缓存失效
                                chatListCache.remove(userId);
                                messageListCache.remove(chatId);
                            })
                            .publishOn(Schedulers.boundedElastic())
                            .thenReturn(true); // .then(Mono.just(true)) 的简写
                });
    }


    /**
     * 消息相关
     */
    @Override
    public Flux<MessageResponseDTO> getMessageList(Long chatId, Long userId) {
        // 同样，先校验权限
        return Mono.fromCallable(() -> openAiMapper.isChatBelong(chatId, userId))
                .publishOn(Schedulers.boundedElastic())
                .flatMapMany(isBelong -> {
                    if (!isBelong) {
                        return Flux.empty(); // 或者 Flux.error(new BusinessException(...))
                    }
                    // 使用响应式缓存
                    return messageListCache.computeIfAbsent(chatId, id ->
                                    Mono.fromCallable(() -> openAiMapper.getMessageList(id))
                                            .publishOn(Schedulers.boundedElastic())
                                            .cache(Duration.ofMinutes(5)) // 缓存消息列表5分钟
                            )
                            .flatMapMany(Flux::fromIterable);
                });
    }

    @Override
    public Flux<ChatResponse> generateStream(Long userId, Long chatId, String model, String message, boolean useTool, Long tagId) {
        // 【核心改造】: 构建一个完整的响应式链，而不是将逻辑放入单独的线程

        // 步骤 1 & 2: 验证权限并保存用户消息，同时使缓存失效
        Mono<Void> initialSetup = Mono.fromCallable(() -> openAiMapper.isChatBelong(chatId, userId))
                .publishOn(Schedulers.boundedElastic())
                .flatMap(isBelong -> {
                    // 1. flatMap 接收上一步的结果 (isBelong)
                    if (!isBelong) {
                        // 2. 如果条件不满足，返回一个错误 Mono。
                        //    这个错误会终止整个流。
                        return Mono.error(new BusinessException(HttpStatus.FORBIDDEN, "无效的Chat ID或用户无权访问"));
                    }

                    // 3. 如果条件满足，返回代表下一个异步操作的 Mono。
                    //    Mono.fromRunnable(...) 返回的是 Mono<Void>
                    return Mono.fromRunnable(() -> {
                        Long userMessageId = snowflake.nextId();
                        openAiMapper.insertMessage(userMessageId, chatId, "user", message);
                        // 使消息列表缓存立即失效
                        messageListCache.remove(chatId);
                        log.info("User message saved and cache cleared for chatId: {}", chatId);
                    }).publishOn(Schedulers.boundedElastic());
                })
                .then(); // 4. 最后使用 .then() 确保整个链的最终类型是 Mono<Void>

        // 步骤 3 & 4: 异步构建 Prompt
        Mono<Prompt> promptMono = Mono.zip(
                        // a) 获取历史消息
                        Mono.fromCallable(() -> openAiMapper.getMessageList(chatId)).publishOn(Schedulers.boundedElastic()),
                        // b) 如果需要，执行RAG搜索
                        Mono.defer(() -> tagId != null ? ragSearch(message, tagId) : Mono.just(""))
                )
                .map(tuple -> {
                    List<MessageResponseDTO> history = tuple.getT1();
                    String ragContext = tuple.getT2();
                    // 在这里构建完整的 messageList 和 Prompt，逻辑与原方法相同
                    // ... (省略了与原方法完全相同的 prompt 构建逻辑)
                    List<Message> messageList = buildMessageList(history, message, ragContext, useTool, tagId);
                    return buildPrompt(messageList, model, useTool);
                });

        // 步骤 5: 链接所有步骤并返回最终的 AI 响应流
        return initialSetup
                .then(promptMono)
                .flatMapMany(prompt -> {
                    StringBuilder fullResponseBuilder = new StringBuilder();

                    return openAiChatModel.stream(prompt)
                            .doOnNext(chatResponse -> {
                                String content = chatResponse.getResult().getOutput().getText();
                                if (content != null) {
                                    fullResponseBuilder.append(content);
                                }
                            })
                            .doOnComplete(() -> {
                                String fullResponse = fullResponseBuilder.toString();
                                if (!fullResponse.isEmpty()) {
                                    // 异步保存AI的完整响应
                                    Mono.fromRunnable(() -> {
                                        Long assistantMessageId = snowflake.nextId();
                                        openAiMapper.insertMessage(assistantMessageId, chatId, "assistant", fullResponse);
                                        // 再次清除缓存，确保新消息可见
                                        messageListCache.remove(chatId);
                                        log.info("AI response saved and cache cleared for chatId: {}", chatId);
                                    }).publishOn(Schedulers.boundedElastic()).subscribe(); // 触发并忘记
                                } else {
                                    log.warn("Stream completed with empty content for chatId: {}", chatId);
                                }
                            });
                });
    }

    // 将 RAG 搜索逻辑提取为一个返回 Mono 的辅助方法
    private Mono<String> ragSearch(String query, Long tagId) {
        return Mono.fromCallable(() -> {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(3)
                    .filterExpression(String.format("knowledge == '%s'", tagId))
                    .build();
            List<Document> documents = pgVectorStore.similaritySearch(request);
            if (documents != null && !documents.isEmpty()) {
                return documents.stream()
                        .map(Document::getFormattedContent)
                        .collect(Collectors.joining("\n---\n"));
            }
            return "未检索到相关信息";
        }).publishOn(Schedulers.boundedElastic());
    }

    private List<Message> buildMessageList(List<MessageResponseDTO> history, String message, String ragContext, boolean useTool, Long tagId) {
        history = history.subList(0, history.size() - 1);
        List<Message> messageList = new ArrayList<>();
        if (!useTool) {
            messageList.add(new SystemMessage(Constant.COMMON_PROMPT_TEMPLATE));
        } else {
            messageList.add(new SystemMessage(Constant.TOOL_PROMPT_TEMPLATE_MAC));
        }
        history.forEach(msg -> {
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
            messageList.add(new UserMessage(Constant.RAG_PROMPT_TEMPLATE
                    .replace("{documents}", ragContext)
                    .replace("{question}", message)));
        }
        return messageList;
    }

    private Prompt buildPrompt(List<Message> messageList, String model, boolean useTool) {
        Prompt prompt;
        if (!useTool) {
            prompt = new Prompt(messageList, OpenAiChatOptions.builder().model(model).build());
        } else {
            ToolCallback[] toolCallbacks = (ToolCallback[]) tools.getToolCallbacks();

            // 【关键修改】: 使用 OpenAiChatOptions.builder()
            OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                    .model(model)
                    // 使用 .withToolCallbacks() 添加您的响应式工具
                    .toolCallbacks(List.of(toolCallbacks))
                    .build();

            prompt = new Prompt(messageList, chatOptions);
        }
        return prompt;
    }

}
