package com.texify.backend.controller;

import com.texify.backend.dto.MessageResponse;
import com.texify.backend.dto.WaitlistRequest;
import com.texify.backend.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public email-capture endpoint for the landing page waitlist.
 * <p>
 * Always returns {@code 200 OK} with a generic message — whether the email was
 * new or already on the list — to avoid leaking who is registered.
 * </p>
 */
@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
@Slf4j
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    public ResponseEntity<MessageResponse> subscribe(@Valid @RequestBody WaitlistRequest request) {
        log.debug("POST /api/waitlist");
        waitlistService.subscribe(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("You're on the list! We'll be in touch soon."));
    }
}
