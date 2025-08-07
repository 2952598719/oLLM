package top.orosirian.service.inf;

public interface UserService {

    boolean isEmailExist(String email);

    void register(String email, String password);

    Long login(String email, String password);

}
