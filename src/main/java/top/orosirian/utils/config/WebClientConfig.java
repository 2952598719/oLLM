package top.orosirian.utils.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    /**
     * 配置 HttpClient 以使用 JVM 的默认 DNS 解析器。
     * 这是解决在特定网络环境（如Docker, K8s, 或有严格防火墙规则的公司内网）中
     * Netty 无法解析 DNS 的关键。
     * @return a customized HttpClient
     */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.create()
                // 使用 DefaultAddressResolverGroup.INSTANCE 来切换到 JVM 的阻塞式 DNS 解析
                .resolver(DefaultAddressResolverGroup.INSTANCE);
    }

    /**
     * 创建一个 WebClient.Builder Bean，它将使用上面定义的自定义 HttpClient。
     * Spring AI 和其他需要 WebClient 的地方会自动注入和使用这个 Builder。
     * @param httpClient the customized HttpClient bean
     * @return a customized WebClient.Builder
     */
    @Bean
    @Primary // 标记为主要 Bean，确保 Spring AI 会优先使用它
    public WebClient.Builder webClientBuilder(HttpClient httpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

}
