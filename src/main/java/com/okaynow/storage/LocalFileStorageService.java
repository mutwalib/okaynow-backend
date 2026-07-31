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

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path root;
    private final long maxBytes;

    public LocalFileStorageService(
            @Value("${app.upload.dir:./uploads}") String uploadDir,
            @Value("${app.upload.max-bytes:2097152}") long maxBytes) throws IOException {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        Files.createDirectories(this.root.resolve("profiles"));
    }

    /** Stores a profile photo and returns a public relative URL path (e.g. /uploads/profiles/…). */
    public String storeProfilePhoto(UUID ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a photo to upload");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("Photo must be 2 MB or smaller");
        }
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.US);
        if (!ALLOWED.contains(contentType)) {
            throw new BadRequestException("Photo must be a JPEG, PNG, or WebP image");
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
}
