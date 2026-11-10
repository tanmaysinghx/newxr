package com.tanmaysinghx.newxr.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByExternalSubject(String subject);

    boolean existsByRoleAndActiveTrue(Role role);

    long countByActiveTrue();

    long countByRole(Role role);

    long countByInvitedTrue();

    @Query("select u from User u where lower(u.displayName) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%')) order by u.displayName")
    List<User> search(String q);

    List<User> findAllByOrderByDisplayNameAsc();
}
