package com.sunasterisk.socialanalytics.repository;

import com.sunasterisk.socialanalytics.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    // Import D2: seed-user fallback — lấy User có id nhỏ nhất (không cần OAuth2)
    Optional<User> findFirstByOrderByIdAsc();
}
