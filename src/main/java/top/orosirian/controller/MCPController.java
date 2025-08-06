package top.orosirian.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mcp")
public class MCPController {

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private ToolCallbackProvider tools;

    @GetMapping("/info")
    public void toolInfo() {
        String userInput = "有哪些工具可以使用";
        var chatClient = chatClientBuilder
                .defaultTools(tools)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("deepseek-chat")
                        .build())
                .build();

        System.out.println("\n>>> QUESTION: " + userInput);
        System.out.println("\n>>> ASSISTANT: " + chatClient.prompt(userInput).call().content());
    }

    @GetMapping("/generate")
    public void test() {
        String userInput = "获取电脑配置";
//        userInput = "在 /Users/fuzhengwei/Desktop 文件夹下，创建 电脑.txt";
        userInput = "利用提供的工具，获取电脑配置信息 在 /Users/peenpjchen/Desktop 文件夹下，创建 电脑.txt 把电脑配置信息写入 电脑.txt";

        var chatClient = chatClientBuilder
                .defaultTools(tools)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("deepseek-chat")
                        .build())
                .build();

        System.out.println("\n>>> QUESTION: " + userInput);
        System.out.println("\n>>> ASSISTANT: " + chatClient.prompt(userInput).call().content());
    }

}
