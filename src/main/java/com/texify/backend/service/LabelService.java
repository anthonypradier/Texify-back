package com.texify.backend.service;

import com.texify.backend.dto.CreateLabelRequest;
import com.texify.backend.dto.LabelResponse;
import com.texify.backend.dto.UpdateLabelRequest;
import com.texify.backend.entity.Label;
import com.texify.backend.entity.User;
import com.texify.backend.exception.LabelNotFoundException;
import com.texify.backend.repository.LabelRepository;
import com.texify.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabelService {

    private final LabelRepository labelRepository;
    private final UserRepository userRepository;

    @Transactional
    public LabelResponse create(String userEmail, CreateLabelRequest request) {
        log.info("Creating label for user '{}'", userEmail);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userEmail));
        Label label = Label.builder()
                .name(request.getName())
                .color(request.getColor())
                .user(user)
                .build();
        return toResponse(labelRepository.save(label));
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> findAll(String userEmail) {
        log.debug("Listing labels for user '{}'", userEmail);
        return labelRepository.findByUserEmail(userEmail)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LabelResponse update(Long id, String userEmail, UpdateLabelRequest request) {
        log.info("Updating label {} for user '{}'", id, userEmail);
        Label label = labelRepository.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new LabelNotFoundException(id));
        if (request.getName() != null) label.setName(request.getName());
        if (request.getColor() != null) label.setColor(request.getColor());
        return toResponse(labelRepository.save(label));
    }

    @Transactional
    public void delete(Long id, String userEmail) {
        log.info("Deleting label {} for user '{}'", id, userEmail);
        Label label = labelRepository.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new LabelNotFoundException(id));
        labelRepository.delete(label);
    }

    private LabelResponse toResponse(Label label) {
        return new LabelResponse(label.getId(), label.getName(), label.getColor());
    }
}
