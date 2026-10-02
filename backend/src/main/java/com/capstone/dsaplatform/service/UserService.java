package com.capstone.dsaplatform.service;

import com.capstone.dsaplatform.dto.UserView;
import com.capstone.dsaplatform.entity.User;
import com.capstone.dsaplatform.error.NotFoundException;
import com.capstone.dsaplatform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAllByOrderByIdAsc().stream()
                .map(u -> new UserView(u.getId(), u.getDisplayName()))
                .toList();
    }

    /** Every user-scoped endpoint starts here, so the 404 message is identical everywhere. */
    @Transactional(readOnly = true)
    public User require(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " does not exist"));
    }
}
