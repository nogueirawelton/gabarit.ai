package com.gabarit.core.user;

import org.springframework.data.jpa.repository.JpaRepository;

// package-private: outros pacotes acessam pelo UserProfileService
interface UserProfileRepository extends JpaRepository<UserProfile, String> {

}
