package com.smartdocs.repository;

import com.smartdocs.entity.Folder;
import com.smartdocs.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByUserAndParentFolderAndDeletedAtIsNull(User user, Folder parentFolder);

    List<Folder> findByUserAndParentFolderIsNullAndDeletedAtIsNull(User user);

    List<Folder> findByUserAndDeletedAtIsNull(User user);

    List<Folder> findByWorkspaceAndParentFolderAndDeletedAtIsNull(com.smartdocs.entity.Workspace workspace, Folder parentFolder);

    List<Folder> findByWorkspaceAndParentFolderIsNullAndDeletedAtIsNull(com.smartdocs.entity.Workspace workspace);

    List<Folder> findByWorkspaceAndDeletedAtIsNull(com.smartdocs.entity.Workspace workspace);

    Optional<Folder> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByNameAndParentFolderAndUserAndDeletedAtIsNull(String name, Folder parentFolder, User user);

    boolean existsByNameAndParentFolderIsNullAndUserAndDeletedAtIsNull(String name, User user);

    boolean existsByNameAndParentFolderAndWorkspaceAndDeletedAtIsNull(String name, Folder parentFolder, com.smartdocs.entity.Workspace workspace);

    boolean existsByNameAndParentFolderIsNullAndWorkspaceAndDeletedAtIsNull(String name, com.smartdocs.entity.Workspace workspace);
}
