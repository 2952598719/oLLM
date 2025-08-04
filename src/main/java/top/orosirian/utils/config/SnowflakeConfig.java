package top.orosirian.utils.config;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SnowflakeConfig {

    @Bean
    public Snowflake snowflake() {
        // 参数: workerId（0-31）, datacenterId（0-31）
        return IdUtil.getSnowflake(1, 1);  // 实际值从配置读取
    }

    public static void main(String[] args) {
        System.out.println(IdUtil.getSnowflake(1, 1).nextId());
    }

}
