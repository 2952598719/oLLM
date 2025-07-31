package top.orosirian.utils;

import lombok.Data;
import top.orosirian.entity.enums.StringType;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

@Data
public class Captcha {

    // 待放入配置类
    private static final int width = 130;
    private static final int height = 38;
    private static final int disturbLine = 10;
    private static final double noiseRate = 0.01;

    private static final Random RANDOM = new Random();

    private int characterCount;

    private String code;

    private BufferedImage bufferedImage;

    public Captcha(int characterCount) {
        this.characterCount = characterCount;
        this.code = StringUtils.getRandomString(StringType.ALL_CHARACTER, characterCount);
        generateCaptcha();
    }

    private void generateCaptcha() {
        int fontWidth = width / this.characterCount;    // 字体的宽度
        int fontHeight = height - 5;                    // 字体的高度
        int codeY = height - 8;

        // 图像设置
        bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = bufferedImage.getGraphics();
        // 1. 设置背景色
        g.setColor(GraphUtils.getRandomColor(200, 250));
        g.fillRect(0, 0, width, height);
        // 2. 设置字体
        Font font = new Font("Fixedsys", Font.BOLD, fontHeight);
        g.setFont(font);
        // 3. 设置干扰线
        for (int i = 0; i < disturbLine; i++) {
            int xs = RANDOM.nextInt(width);
            int ys = RANDOM.nextInt(height);
            int xe = xs + RANDOM.nextInt(width);
            int ye = ys + RANDOM.nextInt(height);
            g.setColor(GraphUtils.getRandomColor(1, 255));
            g.drawLine(xs, ys, xe, ye);
        }
        // 4.添加噪点
        int area = (int) (noiseRate * width * height);
        for (int i = 0; i < area; i++) {
            int x = RANDOM.nextInt(width);
            int y = RANDOM.nextInt(height);
            bufferedImage.setRGB(x, y, RANDOM.nextInt(255));
        }

        // 字符绘制
        for (int i = 0; i < characterCount; i++) {
            String character = code.substring(i, i + 1);
            g.setColor(GraphUtils.getRandomColor(1, 255));
            g.drawString(character, i * fontWidth + 3, codeY);
        }
    }

    // 还可以加入随机字体和扭曲

}
