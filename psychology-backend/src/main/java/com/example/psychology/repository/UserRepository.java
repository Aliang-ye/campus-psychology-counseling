package com.example.psychology.repository;

import com.example.psychology.entity.User;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findById(Long id) {
        List<User> users = jdbcTemplate.query(
                "SELECT * FROM user WHERE id=?",
                new Object[]{id},
                new BeanPropertyRowMapper<>(User.class));
        return users.stream().findFirst();
    }

    public Optional<User> findByUsername(String username) {
        List<User> users = jdbcTemplate.query(
                "SELECT * FROM user WHERE username=?",
                new Object[]{username},
                new BeanPropertyRowMapper<>(User.class));
        return users.stream().findFirst();
    }

    public Optional<User> findByUsernameAndRole(String username, String role) {
        List<User> users = jdbcTemplate.query(
                "SELECT * FROM user WHERE username=? AND role=?",
                new Object[]{username, role},
                new BeanPropertyRowMapper<>(User.class));
        return users.stream().findFirst();
    }

    public void insert(String username, String passwordHash, String role) {
        jdbcTemplate.update(
                "INSERT INTO user (username, password, role) VALUES (?, ?, ?)",
                username, passwordHash, role);
    }

    public void updatePassword(Long userId, String passwordHash) {
        jdbcTemplate.update("UPDATE user SET password=? WHERE id=?", passwordHash, userId);
    }

    public List<User> findStaffVisibleUsers() {
        return jdbcTemplate.query(
                "SELECT * FROM user WHERE role IN ('student','doctor','teacher')",
                new BeanPropertyRowMapper<>(User.class));
    }
}
