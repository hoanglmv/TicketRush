package com.ticketrush.repository;

import com.ticketrush.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
/** 
 * Lớp Repository giao tiếp với Database.
 * Kế thừa Spring Data JPA để cung cấp các hàm thao tác dữ liệu (CRUD) tự động.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
