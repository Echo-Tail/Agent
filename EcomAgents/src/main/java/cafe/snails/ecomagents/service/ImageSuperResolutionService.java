package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.repository.ImageGenerationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
/** 封装图片超分辨率处理能力及文件输出流程。 */
public class ImageSuperResolutionService {

    private static final String DEFAULT_MODE = "base";
    private static final int DEFAULT_UPSCALE_FACTOR = 2;
    private static final String DEFAULT_OUTPUT_FORMAT = "png";
    private static final int MAX_INPUT_LONG_EDGE = 1920;
    private static final int MAX_INPUT_SHORT_EDGE = 1080;
    private static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;
    private static final String ALIYUN_NO_PROXY = "aliyuncs.com,aliyun.com";

    private final ImageGenerationRecordRepository recordRepository;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${image.super-resolution.endpoint:imageenhan.cn-shanghai.aliyuncs.com}")
    private String endpoint;


    public SuperResolutionResult upscale(SuperResolutionRequest request, Long userId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Super-resolution request cannot be empty");
        }

        SourceImage sourceImage = resolveSourceImage(request, userId);
        readAndValidateInputDimensions(sourceImage.bytes());
        String mode = blankToDefault(request.mode(), DEFAULT_MODE);
        int upscaleFactor = request.upscaleFactor() != null ? request.upscaleFactor() : DEFAULT_UPSCALE_FACTOR;
        String outputFormat = blankToDefault(request.outputFormat(), DEFAULT_OUTPUT_FORMAT).toLowerCase();
        boolean saveToLocal = request.saveToLocal() == null || request.saveToLocal();

        if (upscaleFactor < 1 || upscaleFactor > 4) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Upscale factor must be 1, 2, 3, or 4");
        }
        if ("jpeg".equals(outputFormat)) {
            outputFormat = "jpg";
        }
        if (!"png".equals(outputFormat) && !"jpg".equals(outputFormat) && !"bmp".equals(outputFormat)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Output format must be png, jpg, or bmp");
        }
        Integer outputQuality = request.outputQuality();
        if (outputQuality != null && (outputQuality < 30 || outputQuality > 100)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Output quality must be between 30 and 100");
        }

        long start = System.currentTimeMillis();
        String remoteUrl = callAliyun(sourceImage.bytes(), mode, upscaleFactor, outputFormat, outputQuality);
        String savedPath = null;
        Integer width = null;
        Integer height = null;

        if (saveToLocal) {
            savedPath = downloadImage(remoteUrl, outputFormat);
            int[] dimensions = readImageSize(toLocalPath(savedPath));
            if (dimensions != null) {
                width = dimensions[0];
                height = dimensions[1];
            }
        }

        return new SuperResolutionResult(
                sourceImage.reference(),
                remoteUrl,
                savedPath,
                mode,
                upscaleFactor,
                outputFormat,
                width,
                height,
                System.currentTimeMillis() - start
        );
    }

    public int[] validateUploadedSource(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Source image is empty");
        }
        if (imageBytes.length > MAX_INPUT_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Source image must not exceed 10 MB");
        }
        return readAndValidateInputDimensions(imageBytes);
    }

    public int[] validateSourcePath(String sourcePath) {
        return readAndValidateInputDimensions(readSourceBytes(sourcePath));
    }
    public ImageGenerationRecord validateSourceRecord(Long recordId, Long userId) {
        ImageGenerationRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Image generation record not found"));
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No permission to upscale this image");
        }
        String resultPath = record.getResultPathNormalized();
        if (resultPath == null || resultPath.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Image generation record has no image path");
        }
        readAndValidateInputDimensions(readSourceBytes(firstNonBlankLine(resultPath)));
        return record;
    }
    private SourceImage resolveSourceImage(SuperResolutionRequest request, Long userId) {
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            String reference = request.imageUrl().trim();
            return new SourceImage(reference, readSourceBytes(reference));
        }
        if (request.recordId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "recordId or imageUrl is required");
        }

        ImageGenerationRecord record = recordRepository.findById(request.recordId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Image generation record not found"));
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No permission to upscale this image");
        }

        String resultPath = record.getResultPathNormalized();
        if (resultPath == null || resultPath.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Image generation record has no image path");
        }
        String reference = firstNonBlankLine(resultPath);
        return new SourceImage(reference, readSourceBytes(reference));
    }

    private byte[] readSourceBytes(String reference) {
        if (reference.startsWith("http://") || reference.startsWith("https://")) {
            return downloadSourceBytes(reference);
        }

        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        String relative = reference.replace('\\', '/').replaceFirst("^/?uploads/+", "");
        Path sourcePath = uploadRoot.resolve(relative).normalize();
        if (!sourcePath.startsWith(uploadRoot)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Local image path must be inside the upload directory");
        }
        try {
            return readLimited(Files.newInputStream(sourcePath));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unable to read local source image: " + e.getMessage());
        }
    }

    private byte[] downloadSourceBytes(String imageUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(imageUrl).toURL().openConnection(java.net.Proxy.NO_PROXY);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setRequestProperty("Accept", "image/*,*/*");
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(120_000);
            int statusCode = connection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Unable to read source image: HTTP " + statusCode);
            }
            return readLimited(connection.getInputStream());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unable to read source image: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private byte[] readLimited(java.io.InputStream input) throws IOException {
        try (input) {
            byte[] bytes = input.readNBytes(MAX_INPUT_BYTES + 1);
            if (bytes.length > MAX_INPUT_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Source image must not exceed 10 MB");
            }
            if (bytes.length == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Source image is empty");
            }
            return bytes;
        }
    }

    private int[] readAndValidateInputDimensions(byte[] imageBytes) {
        try (var imageInput = javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            var readers = javax.imageio.ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported source image format");
            }
            var reader = readers.next();
            try {
                reader.setInput(imageInput);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateInputDimensions(width, height);
                return new int[]{width, height};
            } finally {
                reader.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Unable to validate source image dimensions: " + e.getMessage());
        }
    }

    private void validateInputDimensions(int width, int height) {
        int longEdge = Math.max(width, height);
        int shortEdge = Math.min(width, height);
        if (longEdge > MAX_INPUT_LONG_EDGE || shortEdge > MAX_INPUT_SHORT_EDGE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Source image dimensions " + width + "x" + height
                            + " exceed Aliyun limits: long edge <= " + MAX_INPUT_LONG_EDGE
                            + " and short edge <= " + MAX_INPUT_SHORT_EDGE);
        }
    }
    private String callAliyun(byte[] imageBytes, String mode, int upscaleFactor,
                              String outputFormat, Integer outputQuality) {
        try (var input = new ByteArrayInputStream(imageBytes)) {
            com.aliyun.imageenhan20190930.Client client = createClient();
            var request = new com.aliyun.imageenhan20190930.models.MakeSuperResolutionImageAdvanceRequest()
                    .setUrlObject(input)
                    .setMode(mode)
                    .setUpscaleFactor((long) upscaleFactor)
                    .setOutputFormat(outputFormat);
            if (outputQuality != null) {
                request.setOutputQuality(outputQuality.longValue());
            }

            var runtime = new com.aliyun.teautil.models.RuntimeOptions()
                    .setNoProxy(ALIYUN_NO_PROXY);
            var response = client.makeSuperResolutionImageAdvance(request, runtime);
            String outputUrl = response.getBody() != null && response.getBody().getData() != null
                    ? response.getBody().getData().getUrl()
                    : null;
            if (outputUrl == null || outputUrl.isBlank()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Aliyun super-resolution response did not include output URL");
            }
            return outputUrl;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Aliyun super-resolution failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Aliyun super-resolution failed: " + e.getMessage());
        }
    }

    private com.aliyun.imageenhan20190930.Client createClient() throws Exception {
        com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client();
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setCredential(credential)
                .setNoProxy(ALIYUN_NO_PROXY);
        config.endpoint = endpoint;
        return new com.aliyun.imageenhan20190930.Client(config);
    }
    private String downloadImage(String imageUrl, String outputFormat) {
        String ext = "jpeg".equals(outputFormat) ? "jpg" : outputFormat;
        Path targetDir = Paths.get(uploadDir, "super-resolution").toAbsolutePath().normalize();
        try {
            Files.createDirectories(targetDir);
            String fileName = UUID.randomUUID() + "." + ext;
            Path target = targetDir.resolve(fileName);

            HttpURLConnection conn = null;
            byte[] imageBytes;
            try {
                URL url = URI.create(imageUrl).toURL();
                conn = (HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Accept", "image/*,*/*");
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(120_000);

                int statusCode = conn.getResponseCode();
                if (statusCode != 200) {
                    throw new IOException("HTTP " + statusCode);
                }
                try (var in = conn.getInputStream()) {
                    imageBytes = in.readAllBytes();
                }
            } finally {
                if (conn != null) conn.disconnect();
            }

            if (imageBytes == null || imageBytes.length == 0) {
                throw new IOException("empty image response");
            }
            Files.write(target, imageBytes);
            return "/uploads/super-resolution/" + fileName;
        } catch (Exception e) {
            log.error("Failed to save super-resolution image from {}", imageUrl, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to save super-resolution image: " + e.getMessage());
        }
    }

    private Path toLocalPath(String savedPath) {
        String clean = savedPath.replace('\\', '/').replaceFirst("^/?uploads/+", "");
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve(clean);
    }

    private int[] readImageSize(Path path) {
        try (var in = javax.imageio.ImageIO.createImageInputStream(path.toFile())) {
            var readers = javax.imageio.ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                var reader = readers.next();
                try {
                    reader.setInput(in);
                    return new int[]{reader.getWidth(0), reader.getHeight(0)};
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to read image size from {}: {}", path, e.getMessage());
        }
        return null;
    }

    private String firstNonBlankLine(String text) {
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) return line.trim();
        }
        return text.trim();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private record SourceImage(String reference, byte[] bytes) {}

    public record SuperResolutionRequest(
            Long recordId,
            String imageUrl,
            String mode,
            Integer upscaleFactor,
            String outputFormat,
            Integer outputQuality,
            Boolean saveToLocal
    ) {}

    public record SuperResolutionResult(
            String sourceUrl,
            String remoteUrl,
            String savedPath,
            String mode,
            int upscaleFactor,
            String outputFormat,
            Integer width,
            Integer height,
            Long timeCostMs
    ) {}
}
