package com.welfarebot.recommendation.repository;

import com.welfarebot.recommendation.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
