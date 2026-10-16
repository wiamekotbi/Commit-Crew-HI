package com.commitcrew.thebakeway.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRegistry extends JpaRepository<BakewayAccount, String> {

    boolean existsByUsername(String username);

    BakewayAccount findByUsername(String username);
}