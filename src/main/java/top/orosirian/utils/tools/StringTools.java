package top.orosirian.utils.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import top.orosirian.model.enums.StringType;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class StringTools {

    private static final String POOL_CHARACTER = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final String POOL_NUMBER = "0123456789";

    private static final Random RANDOM = new SecureRandom();

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public static String getRandomString(StringType stringType, int length) {
        if (length <= 0) {
            return "";
        }

        return switch (stringType) {
            case ALL_CHARACTER -> generateFromPool(POOL_CHARACTER, length);
            case NUMBER -> generateFromPool(POOL_NUMBER, length);
        };
    }

    private static String generateFromPool(String pool, int length) {
        // 使用流API高效生成随机字符串
        List<Character> chars = RANDOM.ints(length, 0, pool.length())
                .mapToObj(pool::charAt)
                .collect(Collectors.toList());
        Collections.shuffle(chars);
        return chars.stream()
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }

    public static String encrypt(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }

}
