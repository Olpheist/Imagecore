package com.green.imagecore.repositories;

import com.green.imagecore.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    // Need a specific query with joins because of the lazy loading
    @Query("""
    select u from User u
    left join fetch u.userRoles ur
    left join fetch ur.role r
    where u.username = :username""")
    Optional<User> findByUsernameWithRoles(@Param("username") String username);

    @Query("""
    select u from User u
    left join fetch u.userRoles ur
    left join fetch ur.role r
    where u.id = :id""")
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    @Query("""
    select u from User u
    left join fetch u.userRoles ur
    left join fetch ur.role r""")
    List<User> findAllWithRoles();
}
