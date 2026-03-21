package com.example.radiomanager.repository;

import com.example.radiomanager.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLogin(String login);

    boolean existsByLogin(String login);

    @Query("SELECT u FROM User u WHERE u.deleted = false")
    List<User> findAllActive();

    @Query("SELECT u FROM User u WHERE u.deleted = false AND u.id = :id")
    Optional<User> findActiveById(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE u.deleted = false " +
            "AND (:login IS NULL OR LOWER(u.login) LIKE LOWER(CONCAT('%', :login, '%'))) " +
            "AND (:fullName IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :fullName, '%'))) " +
            "AND (:role IS NULL OR :role MEMBER OF u.roles) " +
            "AND (:dateFrom IS NULL OR u.registrationDate >= :dateFrom) " +
            "AND (:dateTo IS NULL OR u.registrationDate <= :dateTo)")
    List<User> findActiveWithFilters(@Param("login") String login,
                                     @Param("fullName") String fullName,
                                     @Param("role") String role,
                                     @Param("dateFrom") LocalDateTime dateFrom,
                                     @Param("dateTo") LocalDateTime dateTo);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.deleted = true WHERE u.id = :id")
    void softDelete(@Param("id") Long id);
}