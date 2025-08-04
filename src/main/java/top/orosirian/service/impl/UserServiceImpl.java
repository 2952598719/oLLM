package top.orosirian.service.impl;

import cn.hutool.core.lang.Snowflake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.orosirian.mapper.UserMapper;
import top.orosirian.service.inf.UserService;
import top.orosirian.utils.tools.StringTools;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private Snowflake snowflake;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean isEmailExist(String email) {
        return userMapper.isEmailExist(email);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(String email, String password) {
        if (userMapper.isEmailExist(email)) {
            return;     // 防止重复注册
        }
        Long userId = snowflake.nextId();
        String encryptPassword = StringTools.encrypt(password);
        userMapper.register(userId, email, encryptPassword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long login(String email, String password) {
        String realPassword = userMapper.getPassword(email);
        if (StringTools.matches(password, realPassword)) {
            return userMapper.getUserId(email);
        } else {
            return -1L;
        }
    }

}
