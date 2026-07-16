package com.smartdocs.repository;

import com.smartdocs.entity.LoginHistory;
import com.smartdocs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    List<LoginHistory> findByUserOrderByCreatedAtDesc(User user);

    List<LoginHistory> findTop10ByUserOrderByCreatedAtDesc(User user);

    void deleteAllByUser(User user);
}
