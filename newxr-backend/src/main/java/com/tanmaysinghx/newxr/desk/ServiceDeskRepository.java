package com.tanmaysinghx.newxr.desk;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ServiceDeskRepository extends JpaRepository<ServiceDesk, Long> {
    Optional<ServiceDesk> findByDeskKeyIgnoreCase(String key);
    List<ServiceDesk> findAllByOrderByNameAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ServiceDesk d where d.id = :id")
    Optional<ServiceDesk> lockById(Long id);
}
