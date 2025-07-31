package top.orosirian.utils.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "redis.sdk.config", ignoreInvalidFields = true)
public class RedisClientConfigProperties {

    private String host;

    private String port;

//    private String password;

    private int poolSize;          // 连接池大小

    private int minIdleSize;       // 最小空闲连接数

    private int idleTimeout;    // 连接最大空闲时间ms，超时的连接将被关闭

    private int connectTimeout; // 连接超时时间ms

    private int retryAttempts;      // 连接重试次数

    private int retryInterval;   // 每次重试的间隔时间ms

    private int pingInterval;       // 定期检查连接是否可用的时间间隔ms，0表示不进行检查

    private boolean keepAlive;   // 是否为长连接

}
