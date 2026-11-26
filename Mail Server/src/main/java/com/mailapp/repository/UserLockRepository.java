package com.mailapp.repository;

import com.mailapp.model.User;
import java.util.Optional;

public interface UserLockRepository {
    /** Lock and reload the current row within the caller's write transaction. */
    Optional<User> lockUserById(Long id);
}
