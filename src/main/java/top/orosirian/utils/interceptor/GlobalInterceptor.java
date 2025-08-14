//package top.orosirian.utils.interceptor;
//
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpSession;
//import org.aspectj.lang.ProceedingJoinPoint;
//import org.aspectj.lang.annotation.Around;
//import org.aspectj.lang.annotation.Aspect;
//import org.aspectj.lang.annotation.Pointcut;
//import org.aspectj.lang.reflect.MethodSignature;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.HttpStatus;
//import org.springframework.stereotype.Component;
//import org.springframework.web.context.request.RequestContextHolder;
//import org.springframework.web.context.request.ServletRequestAttributes;
//import top.orosirian.model.annotation.InterceptorAnnotation;
//import top.orosirian.model.annotation.VerifyAnnotation;
//import top.orosirian.utils.BusinessException;
//import top.orosirian.utils.Constant;
//import top.orosirian.utils.tools.TypeTools;
//
//import java.lang.reflect.Array;
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import java.lang.reflect.Parameter;
//import java.util.Collection;
//import java.util.IdentityHashMap;
//import java.util.Map;
//import java.util.Objects;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
////@Slf4j
//@Aspect
//@Component
//public class GlobalInterceptor {
//
//    private static final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();
//
//    private static final Logger log = LoggerFactory.getLogger(GlobalInterceptor.class);
//
//    @Pointcut("@annotation(top.orosirian.model.annotation.InterceptorAnnotation)")
//    private void requestInterceptor() {}
//
//    @Around("requestInterceptor()")
//    public Object intercept(final ProceedingJoinPoint joinPoint) throws Throwable {
//        try {
//            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
//            InterceptorAnnotation interceptor = method.getAnnotation(InterceptorAnnotation.class);
//            if (interceptor == null) {
//                return joinPoint.proceed();
//            }
//            if (interceptor.requireLogin()) {
//                checkLogin();
//            }
//            if(interceptor.requireVerify()) {
//                checkVerify(method.getParameters(), joinPoint.getArgs());
//            }
//            return joinPoint.proceed();
//        } catch (BusinessException e) {
//            throw e;
//        } catch (Exception e) {
//            log.error("校验系统错误", e);
//            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "系统异常");
//        }
//    }
//
//    private void checkLogin() {
//        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
//        HttpSession session = request.getSession();
//        Object isExist = session.getAttribute(Constant.USER_SESSION_KEY);
//        if (isExist == null) {
//            throw new BusinessException(HttpStatus.UNAUTHORIZED);
//        }
//    }
//
//    private void checkVerify(Parameter[] parameters, Object[] arguments) {
//        for (int i = 0; i <= parameters.length - 1; i++) {
//            Object value = arguments[i];
//            Parameter parameter = parameters[i];
//            VerifyAnnotation parameterVerifyAnnotation = parameter.getAnnotation(VerifyAnnotation.class);
//            if (parameterVerifyAnnotation == null) {
//                continue;
//            }
//            if (value == null) {
//                if (parameterVerifyAnnotation.required()) {
//                    throw new BusinessException(HttpStatus.BAD_REQUEST);
//                }
//                continue;
//            }
//
//            Class<?> typeClass = parameter.getType();
//            // 基础类型/String/Object
//
//            if (TypeTools.isPrimitive(typeClass)) {  // 包装类也返回的是true
//                internalCheckPrimitive(parameterVerifyAnnotation, value);
//            } else if (typeClass.equals(String.class)) {
//                internalCheckString(parameterVerifyAnnotation, value);
//            } else {
//                Map<Object, Object> visited = new IdentityHashMap<>();
//                internalCheckObject(value, visited);
//            }
//        }
//    }
//
//    private void internalCheckPrimitive(VerifyAnnotation parameterVerifyAnnotation, Object value) {
//        // checkVerify里检查过是否为null了，此处没必要再检查
//        if (value instanceof Number number) {
//            double numValue = number.doubleValue();
//            if ((parameterVerifyAnnotation.min() != -1 && numValue < parameterVerifyAnnotation.min()) ||
//                    (parameterVerifyAnnotation.max() != -1 && numValue > parameterVerifyAnnotation.max())) {
//                throw new BusinessException(HttpStatus.BAD_REQUEST);
//            }
//        }
//    }
//
//    private void internalCheckString(VerifyAnnotation parameterVerifyAnnotation, Object value) {
//        String str = value.toString();
//
//        // 校验长度
//        if ((parameterVerifyAnnotation.min() != -1 && str.length() < parameterVerifyAnnotation.min()) ||
//            (parameterVerifyAnnotation.max() != -1 && str.length() > parameterVerifyAnnotation.max())) {
//            throw new BusinessException(HttpStatus.BAD_REQUEST);
//        }
//        // 校验正则
//        if (!parameterVerifyAnnotation.regex().getRegex().isEmpty()) {
//            Pattern pattern = patternCache.computeIfAbsent(
//                    parameterVerifyAnnotation.regex().getRegex(),
//                    Pattern::compile
//            );
//            Matcher matcher = pattern.matcher(str);
//            if (!matcher.matches()) {
//                throw new BusinessException(HttpStatus.BAD_REQUEST);
//            }
//        }
//    }
//
//    private void internalCheckObject(Object value, Map<Object, Object> visited) {
//
//        if (value instanceof Collection) {
//            for (Object element : (Collection<?>) value) {
//                objectCheckRecursive(element, visited);
//            }
//        } else if (value.getClass().isArray()) {
//            int length = Array.getLength(value);
//            for (int i = 0; i < length; i++) {
//                Object element = Array.get(value, i);
//                objectCheckRecursive(element, visited);
//            }
//        } else if (value instanceof Map) {
//            for (Object mapValue : ((Map<?, ?>) value).values()) {
//                objectCheckRecursive(mapValue, visited); // 只校验value
//            }
//        } else {
//            objectCheckRecursive(value, visited);
//        }
//    }
//
//    private void objectCheckRecursive(Object obj, Map<Object, Object> visited) {
//        if (obj == null || visited.containsKey(obj)) {
//            return;
//        }
//        visited.put(obj, null);
//        Field[] fields = obj.getClass().getDeclaredFields();
//        for (Field field : fields) {
//            VerifyAnnotation annotation = field.getAnnotation(VerifyAnnotation.class);
//            if (annotation == null) {
//                continue;
//            }
//            try {
//                field.setAccessible(true);
//                Object fieldValue = field.get(obj);
//
//                // 处理null值
//                if (fieldValue == null) {
//                    if (annotation.required()) {
//                        throw new BusinessException(HttpStatus.BAD_REQUEST);
//                    }
//                    continue;
//                }
//
//                // 根据字段类型递归检查
//                Class<?> fieldType = fieldValue.getClass();
//                if (TypeTools.isPrimitive(fieldType)) {
//                    internalCheckPrimitive(annotation, fieldValue);
//                } else if (fieldType.equals(String.class)) {
//                    internalCheckString(annotation, fieldValue);
//                } else {
//                    // 递归检查嵌套对象
//                    internalCheckObject(fieldValue, visited);
//                }
//            } catch (IllegalAccessException e) {
//                throw new RuntimeException("Failed to access field", e);
//            }
//        }
//    }
//
//}
