package com.mailapp.repository;

import com.mailapp.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

public class UserLockRepositoryImpl implements UserLockRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<User> lockUserById(Long id) {
        User user = entityManager.find(User.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) return Optional.empty();
        // A find/locking query can return the already-managed JWT principal under
        // Open EntityManager in View. Refresh with a locking read to get DB values.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(user);
    }
}
