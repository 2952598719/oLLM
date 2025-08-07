package top.orosirian.controller;

import cn.hutool.core.lang.Snowflake;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.orosirian.model.Response.ChatResponseDTO;
import top.orosirian.model.Response.MessageResponseDTO;
import top.orosirian.model.annotation.InterceptorAnnotation;
import top.orosirian.service.inf.IAiService;
import top.orosirian.utils.Constant;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/openai")
// 先不搞session
public class OpenAiController {

    private static final Logger log = LoggerFactory.getLogger(OpenAiController.class);
    @Autowired
    @Qualifier("OpenAiServiceImpl")
    private IAiService openAiService;

    @Autowired
    private Snowflake snowflake;

    /**
     * 对话相关
     */
    // 获取对话列表
    // http://localhost:8090/api/v1/openai/chat_list
    @GetMapping("/chat_list")
    @InterceptorAnnotation(requireLogin = true)
    public ResponseEntity<List<ChatResponseDTO>> chatList(HttpSession session) {
        Long userId = (Long) session.getAttribute(Constant.USER_SESSION_KEY);
        List<ChatResponseDTO> chatList = openAiService.getChatList(userId);
        log.info("对话列表获取成功");
        return ResponseEntity.ok(chatList);
    }

    // 创建对话
    // http://localhost:8090/api/v1/openai/create_chat?prefixString=xxx
    // prefixString为本次用户消息的前10个字
    @PostMapping("/create_chat")
    @InterceptorAnnotation(requireLogin = true)
    public ResponseEntity<Long> createChat(HttpSession session, @RequestParam String prefixString) {
        Long chatId = snowflake.nextId();
        Long userId = (Long) session.getAttribute(Constant.USER_SESSION_KEY);
        openAiService.createChat(chatId, userId, prefixString);
        log.info("对话创建成功");
        return ResponseEntity.ok(chatId);
    }

    // 删除对话
    // http://localhost:8090/api/v1/openai/delete_chat?chatId=xxx
    @DeleteMapping("/delete_chat")
    @InterceptorAnnotation(requireLogin = true)
    public ResponseEntity<String> deleteChat(HttpSession session, @RequestParam String chatId) {
        Long userId = (Long) session.getAttribute(Constant.USER_SESSION_KEY);
        if (openAiService.deleteChat(Long.valueOf(chatId), userId)) {
            log.info("对话删除成功");
            return ResponseEntity.ok("删除成功");
        } else {
            return ResponseEntity.internalServerError().body("删除失败");
        }

    }

    /**
     * 消息相关
     */
    // 获取消息列表
    // http://localhost:8090/api/v1/openai/message_list?chatId=xxx
    @GetMapping("/message_list")
    public ResponseEntity<List<MessageResponseDTO>> messageList(HttpSession session, @RequestParam String chatId) {
        Long userId = (Long) session.getAttribute(Constant.USER_SESSION_KEY);
        List<MessageResponseDTO> messageList = openAiService.getMessageList(Long.valueOf(chatId), userId);
        log.info("消息列表获取成功");
        return ResponseEntity.ok(messageList);
    }

    // 发送并接收消息
    // Flux是个异步数据流，不会不会立即关闭 HTTP 连接，而是将响应拆分成多个 ChatResponse 片段并持续发送
    // http://localhost:8090/api/v1/ollama/generate_stream?chatId=xxx&model=deepseek-chat&message=xxx
    @GetMapping("/generate_stream")
    public SseEmitter generateStream(HttpSession session,
            @RequestParam String chatId, @RequestParam String model, @RequestParam String message,
            @RequestParam boolean useTool, @RequestParam(required = false) String tagId) {
        SseEmitter emitter = new SseEmitter(0L);    // 0L表示永不过时
        // 独立线程执行远程api的请求，避免阻塞web服务
        try (ExecutorService sseMvcExecutor = Executors.newSingleThreadExecutor()) {
            Long userId = (Long) session.getAttribute(Constant.USER_SESSION_KEY);
            log.info("用户 {} 开始请求流式生成，ChatId: {}", userId, chatId);
            sseMvcExecutor.execute(() -> {
                try {
                    Long tagIdNum = tagId == null ? null : Long.parseLong(tagId);
                    openAiService.generateStream(userId, Long.valueOf(chatId), model, message, emitter, useTool, tagIdNum);
                } catch (Exception e) {
                    log.error("在准备流式响应时发生错误. ChatId: {}", chatId, e);
                    emitter.completeWithError(e);   // 如果正常执行，则service内会关闭emitter，因此异常情况下手动关闭
                }
            });
        }
        log.info("SseEmitter for ChatId: {} 已返回给客户端", chatId);
        return emitter;
    }

    // 还可以提供删除消息功能

}
