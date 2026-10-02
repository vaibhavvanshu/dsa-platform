package com.capstone.dsaplatform.repository;

import com.capstone.dsaplatform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    // Stable order so the demo-user switcher never reshuffles.
    List<User> findAllByOrderByIdAsc();
}
