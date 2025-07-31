package top.orosirian.controller.chat;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import top.orosirian.entity.vo.ChatVO;
import top.orosirian.entity.vo.MessageVO;
import top.orosirian.service.inf.chat.IAiService;
import top.orosirian.service.inf.user.UserService;

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/openai")
// 先不搞session
public class OpenAiController {

    @Autowired
    @Qualifier("OpenAiServiceImpl")
    private IAiService openAiService;

    @Autowired
    private UserService userService;

    /**
     * 对话
     */
    // 创建对话
    @PostMapping("/generate_chat")
    public void generateChat(@RequestBody String message) {

    }

    // 获取对话列表
    @GetMapping("/chat_list")
    public List<ChatVO> chatList() {
        return null;
    }

    // 删除对话
    @DeleteMapping("/delete_chat")
    public void deleteChat(@RequestParam Long chatId) {

    }

    /**
     * 消息
     */
    // http://localhost:8090/api/v1/ollama/generate_stream?model=deepseek-r1:1.5b&message=hi
    // 发送并接收消息
    @GetMapping("/generate_stream")
    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam Long chatId, @RequestParam String message) {
        // Flux是个异步数据流，不会不会立即关闭 HTTP 连接，而是将响应拆分成多个 ChatResponse 片段并持续发送
        return openAiService.generateStream(model, message);
    }

    // 获取消息列表
    @GetMapping("/message_list")
    public List<MessageVO> messageList(@RequestParam Long chatId) {
        return null;
    }

    // 删除某条消息
    @DeleteMapping("/delete_message")
    public void deleteMessage(@RequestParam Long messageId) {

    }


    /**
     * rag
     */
    // http://localhost:8090/api/v1/ollama/generate_stream_rag?model=deepseek-r1:1.5b&message=hi
    @GetMapping("/generate_stream_rag")
    public Flux<ChatResponse> generateStreamRag(@RequestParam String model, @RequestParam String ragTag, @RequestParam String message) {
        return openAiService.generateStreamRag(model, ragTag, message);
    }

}
