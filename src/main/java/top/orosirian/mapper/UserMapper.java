package top.orosirian.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    Boolean isEmailExist(String email);

    void register(Long userId, String email, String password);

    String getPassword(String email);

    Long getUserId(String email);

}
