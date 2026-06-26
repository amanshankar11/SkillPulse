package com.skillpulse.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/users")
    public List<AdminUserDtos.UserRow> users(@RequestParam("token") String token) {
        return service.list(token);
    }

    @PutMapping("/api/admin/users/{id}/role")
    public AdminUserDtos.UserRow updateRole(@PathVariable("id") Long id,
                                             @RequestParam("token") String token,
                                             @RequestBody AdminUserDtos.RoleRequest request) {
        return service.updateRole(token, id, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
    }
}
