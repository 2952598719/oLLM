package top.orosirian.utils.tools;

public class TypeTools {

    public static boolean isPrimitive(Class<?> clazz) {
        // 判断是否为基本类型、包装类
        boolean isBasicPrimitive = clazz.isPrimitive();
        boolean isWrappedPrimitive = clazz.equals(Integer.class)
                                    || clazz.equals(Long.class)
                                    || clazz.equals(Double.class)
                                    || clazz.equals(Float.class)
                                    || clazz.equals(Boolean.class)
                                    || clazz.equals(Character.class)
                                    || clazz.equals(Byte.class)
                                    || clazz.equals(Short.class);
        return isBasicPrimitive || isWrappedPrimitive;
    }

}
