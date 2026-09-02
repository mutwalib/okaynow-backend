package com.okaynow.storage;

import com.okaynow.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalFileStorageService {

    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/jpg", "image/pjpeg", "image/png", "image/webp");

    private final Path root;
    private final long maxBytes;

    public LocalFileStorageService(
            @Value("${app.upload.dir:./uploads}") String uploadDir,
            @Value("${app.upload.max-bytes:5242880}") long maxBytes) throws IOException {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        Files.createDirectories(this.root.resolve("profiles"));
        Files.createDirectories(this.root.resolve("caregiver-cv"));
    }

    /** Stores a profile photo and returns a public relative URL path (e.g. /uploads/profiles/…). */
    public String storeProfilePhoto(UUID ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a photo to upload");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(
                    "Photo must be " + (maxBytes / (1024 * 1024)) + " MB or smaller");
        }
        String contentType = resolveContentType(file);
        if (!ALLOWED.contains(contentType)) {
            throw new BadRequestException(
                    "Photo must be a JPEG, PNG, or WebP image (HEIC photos: choose JPEG or use the app update)");
        }
        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String filename = ownerId + "-" + UUID.randomUUID() + "." + ext;
        Path dest = root.resolve("profiles").resolve(filename);
        try {
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BadRequestException("Could not save photo");
        }
        return "/uploads/profiles/" + filename;
    }

    /** Stores an onboarding document (image or PDF) and returns a public relative URL. */
    public String storeOnboardingDocument(UUID ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a file to upload");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(
                    "File must be " + (maxBytes / (1024 * 1024)) + " MB or smaller");
        }
        String contentType = resolveContentType(file);
        String name = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(Locale.US);
        boolean pdf = "application/pdf".equals(contentType) || name.endsWith(".pdf");
        boolean image = ALLOWED.contains(contentType);
        if (!pdf && !image) {
            throw new BadRequestException("Upload a JPEG, PNG, WebP, or PDF file");
        }
        try {
            Files.createDirectories(root.resolve("onboarding"));
        } catch (IOException e) {
            throw new BadRequestException("Could not save file");
        }
        String ext = pdf ? "pdf" : switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String filename = ownerId + "-" + UUID.randomUUID() + "." + ext;
        Path dest = root.resolve("onboarding").resolve(filename);
        try {
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BadRequestException("Could not save file");
        }
        return "/uploads/onboarding/" + filename;
    }

    /** Stores a caregiver CV/resume (PDF or image) and returns a public relative URL. */
    public String storeCaregiverCv(UUID caregiverProfileId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a CV file to upload");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(
                    "CV must be " + (maxBytes / (1024 * 1024)) + " MB or smaller");
        }
        String contentType = resolveContentType(file);
        String name = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(Locale.US);
        boolean pdf = "application/pdf".equals(contentType) || name.endsWith(".pdf");
        boolean image = ALLOWED.contains(contentType);
        if (!pdf && !image) {
            throw new BadRequestException("Upload a PDF or image (JPEG, PNG, WebP)");
        }
        String ext = pdf ? "pdf" : switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String filename = caregiverProfileId + "-" + UUID.randomUUID() + "." + ext;
        Path dest = root.resolve("caregiver-cv").resolve(filename);
        try {
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BadRequestException("Could not save CV");
        }
        return "/uploads/caregiver-cv/" + filename;
    }

    private static String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.US).trim();
        // iOS may report HEIC/HEIF even when the picker has already transcoded
        // the bytes to JPEG.  Treat as JPEG so the allow-list accepts it.
        if ("image/heic".equals(contentType)
                || "image/heif".equals(contentType)
                || "image/heic-sequence".equals(contentType)
                || "image/heif-sequence".equals(contentType)) {
            return "image/jpeg";
        }
        // React Native / some browsers send octet-stream or empty; sniff by filename.
        if (contentType.isEmpty()
                || "application/octet-stream".equals(contentType)
                || "binary/octet-stream".equals(contentType)) {
            String name = file.getOriginalFilename() == null
                    ? ""
                    : file.getOriginalFilename().toLowerCase(Locale.US);
            if (name.endsWith(".png")) {
                return "image/png";
            }
            if (name.endsWith(".webp")) {
                return "image/webp";
            }
            if (name.endsWith(".heic") || name.endsWith(".heif")) {
                return "image/jpeg";
            }
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                return "image/jpeg";
            }
        }
        if ("image/jpg".equals(contentType) || "image/pjpeg".equals(contentType)) {
            return "image/jpeg";
        }
        return contentType;
    }
}
