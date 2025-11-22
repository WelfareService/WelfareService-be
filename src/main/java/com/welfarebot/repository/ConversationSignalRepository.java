package com.welfarebot.repository;

import com.welfarebot.model.ConversationSignal;
import com.welfarebot.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationSignalRepository extends JpaRepository<ConversationSignal, Long> {

    List<ConversationSignal> findTop5ByUserOrderByCreatedAtDesc(User user);
}
