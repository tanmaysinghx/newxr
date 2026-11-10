package com.tanmaysinghx.newxr.pages;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceRepository extends JpaRepository<Space, Long> {
    Optional<Space> findBySpaceKeyIgnoreCase(String key);
    boolean existsBySpaceKeyIgnoreCase(String key);
    List<Space> findByArchivedOrderByNameAsc(boolean archived);
}
