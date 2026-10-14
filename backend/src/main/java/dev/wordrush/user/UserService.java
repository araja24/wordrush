package dev.wordrush.user;

import dev.wordrush.common.error.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    static final class UserNotFoundException extends DomainException {
        UserNotFoundException(UUID id) {
            super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User " + id + " does not exist");
        }
    }

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return users.findById(id).map(UserDto::from).orElseThrow(() -> new UserNotFoundException(id));
    }
}
