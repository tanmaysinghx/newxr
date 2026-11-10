package com.tanmaysinghx.newxr.pages;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageVersionRepository extends JpaRepository<PageVersion, Long> {
    List<PageVersion> findByPageOrderByVersionDesc(Page page);
    void deleteByPage(Page page);
}
