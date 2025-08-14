package top.orosirian.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GitRequest {

    @NotBlank(message = "仓库地址不能为空")
    private String repoUrl;

    @NotBlank(message = "用户名不能为空")
    private String userName;

    @NotBlank(message = "Token 不能为空")
    private String token;

}
