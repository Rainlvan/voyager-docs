package com.voyager.docs.web;

import com.voyager.docs.dto.UserDtos;
import com.voyager.docs.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AccountService accountService;

    public AdminUserController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<UserDtos.ManagedUserResponse> list() {
        return accountService.listEmployees();
    }

    @PostMapping
    public UserDtos.ManagedUserResponse create(@Valid @RequestBody UserDtos.CreateUserRequest request) {
        return accountService.createEmployee(request);
    }

    @PutMapping("/{id}")
    public UserDtos.ManagedUserResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UserDtos.UpdateUserRequest request) {
        return accountService.updateEmployee(id, request);
    }

    @PatchMapping("/{id}/enabled")
    public UserDtos.ManagedUserResponse enabled(
            @PathVariable Long id,
            @RequestBody UserDtos.EnabledRequest request) {
        return accountService.setEmployeeEnabled(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        accountService.deleteEmployee(id);
    }
}
