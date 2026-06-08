package com.voyager.docs.web;

import com.voyager.docs.dto.AuthDtos;
import com.voyager.docs.service.AccountService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AuthDtos.UserResponse uploadAvatar(@RequestParam("file") MultipartFile file) {
        return accountService.uploadAvatar(file);
    }

    @GetMapping("/avatar/{userId}")
    public ResponseEntity<InputStreamResource> avatar(@PathVariable Long userId) {
        AccountService.AvatarObject avatar = accountService.readAvatar(userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(new InputStreamResource(avatar.stream()));
    }
}
