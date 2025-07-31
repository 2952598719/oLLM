package top.orosirian.utils;

import java.awt.*;
import java.util.Random;

public class GraphUtils {

    private static final Random random = new Random();

    public static Color getRandomColor(int low, int high) {// 给定范围获得随机颜色
        if (low > 255) low = 255;
        if (high > 255) high = 255;
        int r = low + random.nextInt(high - low);
        int g = low + random.nextInt(high - low);
        int b = low + random.nextInt(high - low);
        return new Color(r, g, b);
    }

}
