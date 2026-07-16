package com.smartdocs.repository;

import com.smartdocs.entity.Document;
import com.smartdocs.entity.Share;
import com.smartdocs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShareRepository extends JpaRepository<Share, Long> {

    List<Share> findBySharedWithEmail(String email);

    List<Share> findBySharedBy(User sharedBy);

    boolean existsByDocumentAndSharedWithEmail(Document document, String email);

    Optional<Share> findByDocumentAndSharedWithEmail(Document document, String email);

    List<Share> findAllByDocument(Document document);
}
