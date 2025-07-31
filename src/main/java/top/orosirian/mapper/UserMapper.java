package top.orosirian.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    boolean isEmailExist(String email);

}
