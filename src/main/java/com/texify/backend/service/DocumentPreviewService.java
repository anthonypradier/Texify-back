package com.texify.backend.service;

import com.texify.backend.entity.Document;
import com.texify.backend.entity.PreviewStatus;
import com.texify.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

/**
 * Owns the document preview lifecycle. Mirrors {@code TemplatePreviewService}
 * but, unlike templates, previews are regenerated only after a successful
 * compilation (which the user triggers), never automatically on save.
 * <p>
 * The LaTeX compiler (tectonic) is not wired yet, so {@link #generatePreviewAsync}
 * is a stub: the integration hook {@link #onCompilationSuccess} is the single
 * entry point the future compilation pipeline will call.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentPreviewService {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;

    /**
     * Called by the compilation pipeline after a successful compile. Flags the
     * preview as GENERATING then kicks off the (stubbed) PNG extraction.
     *
     * @param documentId the document whose PDF was just compiled
     * @param pdfPath    relative path of the compiled PDF in the storage
     */
    @Transactional
    public void onCompilationSuccess(Long documentId, String pdfPath) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setPreviewStatus(PreviewStatus.GENERATING);
            documentRepository.save(doc);
        });
        generatePreviewAsync(documentId, pdfPath);
    }

    /**
     * STUB — extracts page 1 of the PDF as a PNG via PDFBox.
     * <p>
     * Planned flow: {@code StorageService.load(pdfPath)} → PDFBox
     * {@code PDFRenderer.renderImageWithDPI(0, 150)} → {@code ImageIO.write(PNG)}
     * → {@code StorageService.store(png, buildPreviewPath(id))} → set
     * {@code previewImagePath}, {@code previewGeneratedAt}, status READY.
     * Dependency to add when the compiler arrives: {@code org.apache.pdfbox:pdfbox:3.0.x}.
     * </p>
     * <p>
     * Note: invoked from {@link #onCompilationSuccess} on the same bean, so the
     * {@code @Async} proxy is bypassed for now (runs inline) — harmless while
     * this is a stub; the real compiler integration will call it from outside.
     * </p>
     */
    @Async
    @Transactional
    public CompletableFuture<Void> generatePreviewAsync(Long documentId, String pdfPath) {
        log.info("STUB: generatePreviewAsync for document {} — compiler not available", documentId);

        // TODO: extract the PNG with PDFBox and mark the document READY.
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setPreviewStatus(PreviewStatus.PENDING);
            documentRepository.save(doc);
        });

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Marks the preview as stale when the document blocks change. The old
     * preview stays visible (OUTDATED) until the next compilation — no automatic
     * regeneration, since the user decides when to compile.
     */
    @Transactional
    public void invalidatePreview(Long documentId) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            if (doc.getPreviewStatus() == PreviewStatus.READY) {
                doc.setPreviewStatus(PreviewStatus.OUTDATED);
                documentRepository.save(doc);
            }
        });
    }

    /** Deletes a document's preview files (call before a hard delete). */
    public void deletePreviewFiles(Document document) {
        storageService.delete(document.getPreviewImagePath());
    }

    private String buildPreviewPath(Long documentId) {
        return "documents/previews/" + documentId + "/preview.png";
    }
}
