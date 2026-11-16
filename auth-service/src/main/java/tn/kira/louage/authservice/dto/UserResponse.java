package tn.kira.louage.authservice.dto;

import tn.kira.louage.authservice.entity.Role;

public record UserResponse(Long id, String username, Role role) {
}
