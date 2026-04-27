package com.green.imagecore.repositories;

import com.green.imagecore.entities.Role;
import com.green.imagecore.entities.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleType name);
}
