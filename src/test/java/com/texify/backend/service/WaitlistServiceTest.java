package com.texify.backend.service;

import com.texify.backend.entity.WaitlistEntry;
import com.texify.backend.repository.WaitlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock private WaitlistRepository waitlistRepository;

    @InjectMocks
    private WaitlistService waitlistService;

    @Test
    @DisplayName("subscribe: new email is normalised (trim + lowercase) then saved")
    void subscribe_newEmail_savesNormalized() {
        when(waitlistRepository.existsByEmail("alice@example.com")).thenReturn(false);

        waitlistService.subscribe("  Alice@Example.COM ");

        ArgumentCaptor<WaitlistEntry> captor = ArgumentCaptor.forClass(WaitlistEntry.class);
        verify(waitlistRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("subscribe: already-registered email is not saved again (idempotent)")
    void subscribe_existingEmail_doesNotSave() {
        when(waitlistRepository.existsByEmail("bob@example.com")).thenReturn(true);

        waitlistService.subscribe("bob@example.com");

        verify(waitlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("subscribe: concurrent duplicate (UNIQUE violation) is swallowed")
    void subscribe_concurrentDuplicate_swallowsException() {
        when(waitlistRepository.existsByEmail("carol@example.com")).thenReturn(false);
        when(waitlistRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatCode(() -> waitlistService.subscribe("carol@example.com"))
                .doesNotThrowAnyException();
    }
}
