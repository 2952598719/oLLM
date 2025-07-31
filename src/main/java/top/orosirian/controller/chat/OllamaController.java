package top.orosirian.controller.chat;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import top.orosirian.service.inf.chat.IAiService;

// 因为要部署在远程服务器上，这里暂时就不修改了。以后有能部署llm的服务器了，再修改这里

@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/ollama")

public class OllamaController {

    @Autowired
    @Qualifier("OllamaServiceImpl")
    private IAiService ollamaService;

    // http://localhost:8090/api/v1/ollama/generate?model=deepseek-r1:1.5b&message=1+1
    @GetMapping("/generate")
    public ChatResponse generate(@RequestParam String model, @RequestParam String message) {
        return ollamaService.generate(model, message);
    }

    // http://localhost:8090/api/v1/ollama/generate_stream?model=deepseek-r1:1.5b&message=hi
    // Flux是个异步数据流，不会不会立即关闭 HTTP 连接，而是将响应拆分成多个 ChatResponse 片段并持续发送
    @GetMapping("/generate_stream")
    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String chatId,@RequestParam String message) {
        return ollamaService.generateStream(model, message);
    }

    // http://localhost:8090/api/v1/ollama/generate_stream_rag?model=deepseek-r1:1.5b&message=hi
    @GetMapping("/generate_stream_rag")
    public Flux<ChatResponse> generateStreamRag(@RequestParam String model, @RequestParam String ragTag, @RequestParam String message) {
        return ollamaService.generateStreamRag(model, ragTag, message);
    }

}
