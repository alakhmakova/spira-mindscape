package com.spiramindscape.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByGoogleSub(String googleSub);

    Optional<AppUser> findByEmail(String email);
}
