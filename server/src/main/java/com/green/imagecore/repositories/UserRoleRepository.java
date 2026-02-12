package com.green.imagecore.repositories;

import com.green.imagecore.entities.UserRole;
import com.green.imagecore.entities.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

}
