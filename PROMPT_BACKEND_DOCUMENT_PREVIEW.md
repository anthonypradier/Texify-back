# Prompt Agent Backend — Extension du système de preview aux documents

---

## Contexte

Le système de preview des templates est déjà en place :
- `StorageService` — stockage local avec path traversal protection
- `PreviewStatus` enum — PENDING / GENERATING / READY / OUTDATED / ERROR
- `TemplatePreviewService` — upload manuel + stub `generatePreviewAsync`
- Structure de stockage : `storage/templates/previews/{id}/preview.png`

Tu dois **étendre** ce système aux documents normaux, en ajoutant la génération
de preview (PNG page 1) après chaque compilation réussie.

Le compilateur tectonic **n'est pas encore implémenté** — le hook de génération
de preview doit être prévu mais stubbé, exactement comme pour les templates.

---

## RÈGLES

- Réutiliser `StorageService` tel quel — aucune modification
- Réutiliser `PreviewStatus` tel quel
- Ne pas toucher à `TemplatePreviewService`
- Mettre à jour `CLAUDE.md`

---

## 1. MODIFICATIONS DE L'ENTITÉ `Document`

Ajouter dans `Document.java` :

```java
/**
 * Chemin relatif du PNG de preview (page 1 du dernier PDF compilé).
 * Null = document jamais compilé ou compilation en attente.
 * Écrasé à chaque compilation réussie.
 */
@Column(nullable = true, length = 500)
private String previewImagePath;

/**
 * Date de génération de la preview courante.
 * Null = aucune preview disponible.
 */
@Column(nullable = true)
private LocalDateTime previewGeneratedAt;

/**
 * Statut de la preview.
 * Mis à jour par DocumentPreviewService au fil du pipeline de compilation.
 */
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private PreviewStatus previewStatus = PreviewStatus.PENDING;
```

**Migration** `V8__alter_document_add_preview_fields.sql` :
```sql
ALTER TABLE documents
    ADD COLUMN preview_image_path    VARCHAR(500)  NULL,
    ADD COLUMN preview_generated_at  DATETIME      NULL,
    ADD COLUMN preview_status        VARCHAR(15)   NOT NULL DEFAULT 'PENDING';
```

---

## 2. SERVICE — `DocumentPreviewService.java`

Calqué sur `TemplatePreviewService`. Créer dans `service/` :

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentPreviewService {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;

    /**
     * Point d'entrée appelé par le pipeline de compilation après un succès.
     * Lance la génération de preview en arrière-plan.
     *
     * @param documentId ID du document dont le PDF vient d'être compilé
     * @param pdfPath    Chemin relatif du PDF compilé dans le stockage
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
     * STUB — Extraction de la page 1 du PDF en PNG via PDFBox.
     *
     * À implémenter quand tectonic sera disponible.
     * Flux prévu :
     *   1. StorageService.load(pdfPath) → flux PDF
     *   2. PDFBox : PDDocument.load() → PDFRenderer → renderImageWithDPI(0, 150)
     *   3. ImageIO.write(image, "PNG", outputStream)
     *   4. StorageService.store(pngBytes, buildPreviewPath(documentId))
     *   5. Mettre à jour Document : previewImagePath, previewGeneratedAt, previewStatus=READY
     *
     * Dépendance à ajouter dans pom.xml quand le compilateur arrive :
     *   org.apache.pdfbox:pdfbox:3.0.x
     *
     * @param documentId ID du document
     * @param pdfPath    Chemin relatif du PDF source dans le stockage
     */
    @Async
    public CompletableFuture<Void> generatePreviewAsync(Long documentId, String pdfPath) {
        log.info("STUB: generatePreviewAsync for document {} — compilateur non disponible", documentId);

        // TODO : implémenter l'extraction PNG avec PDFBox
        // En attendant, remettre en PENDING
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setPreviewStatus(PreviewStatus.PENDING);
            documentRepository.save(doc);
        });

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Invalide la preview quand les blocs d'un document sont modifiés.
     * La preview reste affichée (OUTDATED) jusqu'à la prochaine compilation.
     * Contrairement aux templates, on ne relance pas de génération automatique —
     * c'est l'utilisateur qui décide quand compiler.
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

    /**
     * Supprime les fichiers de preview lors de la suppression définitive d'un document.
     */
    public void deletePreviewFiles(Document document) {
        storageService.delete(document.getPreviewImagePath());
    }

    private String buildPreviewPath(Long documentId) {
        return "documents/previews/" + documentId + "/preview.png";
    }
}
```

---

## 3. NOUVEAU ENDPOINT — `DocumentController.java`

Ajouter dans le contrôleur existant :

```java
/**
 * Statut de preview d'un document — pour le polling frontend.
 * Accessible uniquement par le propriétaire du document.
 */
@GetMapping("/{id}/preview/status")
@Operation(summary = "Statut de preview d'un document")
public ResponseEntity<Map<String, Object>> getPreviewStatus(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails) {

    Document doc = documentService.getDocumentForUser(id, userDetails);

    return ResponseEntity.ok(Map.of(
        "documentId",     id,
        "previewStatus",  doc.getPreviewStatus(),
        "previewImageUrl", storageService.buildPublicUrl(doc.getPreviewImagePath()),
        "generatedAt",    doc.getPreviewGeneratedAt()
    ));
}
```

---

## 4. MODIFICATIONS — `DocumentService.java`

### 4a. Dans la méthode de mise à jour (`updateDocument`)

Après `document.setBlocks(...)`, ajouter :
```java
documentPreviewService.invalidatePreview(document.getId());
```

### 4b. Dans la méthode de suppression définitive

Avant la suppression physique, ajouter :
```java
documentPreviewService.deletePreviewFiles(document);
```

### 4c. Stub du pipeline de compilation (à créer si absent)

Si la méthode `compileDocument()` n'existe pas encore, la créer en stub :

```java
/**
 * STUB — Lance la compilation d'un document via tectonic.
 * Retourne immédiatement un compilationId (202 Accepted).
 * La compilation réelle et l'appel à documentPreviewService.onCompilationSuccess()
 * seront implémentés avec le compilateur.
 */
@Transactional
public Map<String, Object> compileDocument(Long documentId, UserDetails userDetails) {
    Document doc = getDocumentForUser(documentId, userDetails);

    // Créer un CompilationLog en statut QUEUED
    CompilationLog log = CompilationLog.builder()
            .user(doc.getOwner())
            .document(doc)
            .status(CompilationStatus.QUEUED)
            .engine("tectonic")
            .build();
    compilationLogRepository.save(log);

    // TODO : lancer le job de compilation async
    // Le job appellera documentPreviewService.onCompilationSuccess(documentId, pdfPath)
    // en cas de succès, ou mettra le log en ERROR en cas d'échec.

    return Map.of(
        "compilationId", log.getId(),
        "status", "QUEUED",
        "message", "Compilation en attente — compilateur non disponible"
    );
}
```

---

## 5. MISE À JOUR DES DTOs

**`DocumentDto.java`** — ajouter les champs de preview :

```java
public record DocumentDto(
    Long id,
    String title,
    String blocks,
    Long ownerId,
    boolean isPublic,
    String compiledPdfPath,
    Integer wordCount,
    // ── Preview ──────────────────────────────
    String previewImageUrl,       // URL publique construite par StorageService (nullable)
    PreviewStatus previewStatus,  // état courant de la preview
    // ─────────────────────────────────────────
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**`DocumentMapper.java`** — dans la méthode de mapping, construire `previewImageUrl` :
```java
// Appeler storageService.buildPublicUrl(document.getPreviewImagePath())
// pour obtenir l'URL absolue depuis le chemin relatif en base
```

---

## 6. MISE À JOUR `CLAUDE.md`

```markdown
## Preview des documents

### Principe
Générée après chaque compilation réussie (pas à chaque sauvegarde).
La preview montre l'état du document tel que compilé, pas le brouillon en cours.
previewStatus=OUTDATED : preview disponible mais blocs modifiés depuis — afficher
l'ancienne preview avec un indicateur visuel "recompiler pour mettre à jour".

### Stockage
documents/previews/{documentId}/preview.png
Écrasé à chaque compilation réussie (pas d'accumulation).

### Intégration avec le pipeline de compilation
Quand tectonic sera disponible, appeler après compilation réussie :
  documentPreviewService.onCompilationSuccess(documentId, pdfPath)
Cette méthode déclenche l'extraction PNG via PDFBox en @Async.
Dépendance PDFBox à ajouter : org.apache.pdfbox:pdfbox:3.0.x

### Statuts
PENDING  → jamais compilé
OUTDATED → preview existante mais blocs modifiés (afficher avec badge "À recompiler")
READY    → preview à jour
GENERATING → extraction PNG en cours (polling frontend)
ERROR    → extraction échouée

### Endpoint de polling
GET /api/documents/{id}/preview/status → { previewStatus, previewImageUrl, generatedAt }
```

---

*Prompt agent backend — Texify SaaS, extension preview aux documents*
