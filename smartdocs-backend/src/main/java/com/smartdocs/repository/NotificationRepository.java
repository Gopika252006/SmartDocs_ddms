package com.smartdocs.repository;

import com.smartdocs.entity.Notification;
import com.smartdocs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    List<Notification> findByTypeOrderByCreatedAtDesc(String type);

    @Query("SELECT n FROM Notification n WHERE n.user = :user OR (n.user IS NULL AND n.type = :type AND (:workspace IS NULL OR n.workspace = :workspace)) ORDER BY n.createdAt DESC")
    List<Notification> findNotificationsForUser(@Param("user") User user, @Param("type") String type, @Param("workspace") com.smartdocs.entity.Workspace workspace);

    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.user = :user AND n.isRead = false) OR (n.user IS NULL AND n.type = :type AND n.isRead = false AND (:workspace IS NULL OR n.workspace = :workspace))")
    long countUnreadNotificationsForUser(@Param("user") User user, @Param("type") String type, @Param("workspace") com.smartdocs.entity.Workspace workspace);

    void deleteAllByUser(User user);
}
