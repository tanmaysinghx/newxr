package com.tanmaysinghx.newxr.desk;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestTypeRepository extends JpaRepository<RequestType, Long> {
    List<RequestType> findByDeskOrderByIdAsc(ServiceDesk desk);
}
