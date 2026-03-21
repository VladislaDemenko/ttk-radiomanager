// src/main/java/com/example/radiomanager/model/User.java
package com.example.radiomanager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String login;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String password;

    @Column(name = "registration_date", nullable = false)
    private LocalDateTime registrationDate;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles = new HashSet<>();

    public User(String login, String fullName, String password, LocalDateTime registrationDate) {
        this.login = login;
        this.fullName = fullName;
        this.password = password;
        this.registrationDate = registrationDate;
        this.roles = new HashSet<>();
        this.roles.add("USER");
        this.deleted = false;
    }

    public String getRole() {
        return roles.isEmpty() ? "USER" : roles.iterator().next();
    }
}