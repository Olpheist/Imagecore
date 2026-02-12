package com.green.imagecore.repositories;

import com.green.imagecore.entities.RolePermission;
import com.green.imagecore.entities.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

}
