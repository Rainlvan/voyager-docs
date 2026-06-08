package com.voyager.docs.web;

import com.voyager.docs.dto.AuthDtos;
import com.voyager.docs.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/profile")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProfileController {
    private final AccountService accountService;

    public AdminProfileController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PutMapping
    public AuthDtos.LoginResponse update(@Valid @RequestBody AuthDtos.UpdateAdminProfileRequest request) {
        return accountService.updateAdminProfile(request);
    }
}
