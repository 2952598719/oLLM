package top.orosirian.utils.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisClientConfig {

    @Value("${redis.sdk.config.host}")
    private String host;

    @Value("${redis.sdk.config.port}")
    private Integer port;

//    private String password;

    @Value("${redis.sdk.config.pool-size}")
    private Integer poolSize;          // 连接池大小

    @Value("${redis.sdk.config.min-idle-size}")
    private Integer minIdleSize;       // 最小空闲连接数

    @Value("${redis.sdk.config.idle-timeout}")
    private Integer idleTimeout;    // 连接最大空闲时间ms，超时的连接将被关闭

    @Value("${redis.sdk.config.connect-timeout}")
    private Integer connectTimeout; // 连接超时时间ms

    @Value("${redis.sdk.config.retry-attempts}")
    private Integer retryAttempts;      // 连接重试次数

    @Value("${redis.sdk.config.retry-interval}")
    private Integer retryInterval;   // 每次重试的间隔时间ms

    @Value("${redis.sdk.config.ping-interval}")
    private Integer pingInterval;       // 定期检查连接是否可用的时间间隔ms，0表示不进行检查

    @Value("${redis.sdk.config.keep-alive}")
    private Boolean keepAlive;   // 是否为长连接

//    @Bean("redissonClient")
//    public RedissonClient redissonCache() {
//        Config config = new Config();
//        config.setCodec(JsonJacksonCodec.INSTANCE);
//        config.useSingleServer()
//                .setAddress("redis://" + host + ":" + port)
////                .setPassword(properties.getPassword())
//                .setConnectionPoolSize(poolSize)
//                .setConnectionMinimumIdleSize(minIdleSize)
//                .setIdleConnectionTimeout(idleTimeout)
//                .setConnectTimeout(connectTimeout)
//                .setRetryAttempts(retryAttempts)
//                .setRetryInterval(retryInterval)
//                .setPingConnectionInterval(pingInterval)
//                .setKeepAlive(keepAlive);
//
//        return Redisson.create(config);
//    }

    @Bean(destroyMethod = "shutdown")
    public RedissonReactiveClient redissonReactiveClient(RedissonClient redissonClient) {
        return redissonClient.reactive();
    }

}
