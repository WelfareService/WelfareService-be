package com.welfarebot.recommendation.repository;

import com.welfarebot.recommendation.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByName(String name);
}
