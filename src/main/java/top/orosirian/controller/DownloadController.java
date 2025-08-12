package top.orosirian.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1/download")
public class DownloadController {

    @GetMapping("/resource")
    public ResponseEntity<Resource> getResource(@RequestParam String resourceType, @RequestParam String resourceName) {

        // 验证资源类型
        if (!resourceType.equals("music") && !resourceType.equals("novel")) {
            return ResponseEntity
                    .badRequest()
                    .body(null);
        }

        // 构建文件路径
        String osName = System.getProperty("os.name").toLowerCase();
        String userName = System.getProperty("user.name");
        String suffix = resourceType.equals("music") ? ".mp3" : ".txt";
        String basePath = "";

        if (osName.contains("win")) {
            basePath = String.format("C:/Users/%s/Downloads/%s/", userName, resourceType);
        } else if (osName.contains("mac")) {
            basePath = String.format("/Users/%s/MyResources/%s/", userName, resourceType);
        } else if (osName.contains("nix") || osName.contains("nux")) {
            basePath = String.format("/home/%s/MyResources/%s/", userName, resourceType);
        } else {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }

        // 创建完整路径并检查文件存在性
        Path filePath = Paths.get(basePath + resourceName + suffix);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }

        // 创建文件资源
        FileSystemResource resource = new FileSystemResource(filePath);

        // 准备响应头
        HttpHeaders headers = new HttpHeaders();

        // 设置内容类型
        if (resourceType.equals("music")) {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        } else {
            MediaType mediaType = new MediaType("text", "plain", StandardCharsets.UTF_8);
            headers.setContentType(mediaType);
        }

        // 设置文件名 - 使用传统编码方式解决Postman兼容性问题
        try {
            String fullFileName = resourceName + suffix;
            String encodedFileName = URLEncoder.encode(fullFileName, StandardCharsets.UTF_8);

            // 使用传统Content-Disposition格式
            String contentDisposition = "attachment; filename=\"" + encodedFileName + "\"";
            headers.set("Content-Disposition", contentDisposition);

            // 添加Windows/Mac兼容文件名
            headers.add("Content-Disposition", "attachment; filename=\"" +
                    fullFileName.replace("\"", "\\\"") + "\"");

        } catch (Exception e) {
            // 回退到简单处理
            headers.setContentDisposition(
                    ContentDisposition.builder("attachment")
                            .filename(resourceName + suffix)
                            .build()
            );
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

}
