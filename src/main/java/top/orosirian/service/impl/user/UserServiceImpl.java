package top.orosirian.service.impl.user;

import org.springframework.stereotype.Service;
import top.orosirian.service.inf.user.UserService;

@Service
public class UserServiceImpl implements UserService {

    public boolean isEmailExist(String email) {
        return true;
    }



}
