package in.co.kitree.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Shared Cloudinary upload service.
 * Provides server-side image/file uploads with public read URLs.
 */
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final boolean isTest;

    public CloudinaryService(boolean isTest) {
        String cloudinaryUrl = loadCloudinaryUrl();
        if (cloudinaryUrl == null || cloudinaryUrl.isEmpty()) {
            throw new RuntimeException("CLOUDINARY_URL not configured in secrets.json");
        }
        this.cloudinary = new Cloudinary(cloudinaryUrl);
        this.cloudinary.config.secure = true;
        this.isTest = isTest;
    }

    /**
     * Upload an image (as bytes) to Cloudinary.
     *
     * @param imageBytes raw image bytes
     * @param folder     logical folder (e.g. "experts", "products")
     * @param publicId   public ID within the folder
     * @return the public secure URL of the uploaded image
     */
    public String uploadImage(byte[] imageBytes, String folder, String publicId) throws Exception {
        String path = isTest ? "test/" : "";
        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult = cloudinary.uploader().upload(
                imageBytes,
                ObjectUtils.asMap(
                        "public_id", path + folder + "/" + publicId,
                        "unique_filename", false,
                        "overwrite", true
                )
        );
        return String.valueOf(uploadResult.get("secure_url"));
    }

    private static String loadCloudinaryUrl() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(new File("secrets.json"));
            return rootNode.path("CLOUDINARY_URL").asText("");
        } catch (IOException e) {
            LoggingService.error("cloudinary_secrets_read_failed", e);
            return null;
        }
    }
}
