package cafe.snails.ecomagents.service.image.runtime.storage;

import cafe.snails.ecomagents.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.util.UUID;

@Component
/** 负责保存图片生成结果并创建素材记录。 */
public class ImageOutputStorage {
    @Value("${file.upload-dir:./uploads}") private String uploadDir;
    public String store(Long jobId, int index, byte[] content, String mimeType) {
        if (content == null || content.length == 0) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "供应商返回了空图片");
        String extension = "image/jpeg".equalsIgnoreCase(mimeType) ? ".jpg" :
                "image/webp".equalsIgnoreCase(mimeType) ? ".webp" : ".png";
        try {
            Path directory = Paths.get(uploadDir, "image-jobs", jobId.toString(), "outputs").toAbsolutePath().normalize();
            Files.createDirectories(directory);
            String filename = String.format("%02d-%s%s", index, UUID.randomUUID(), extension);
            Files.write(directory.resolve(filename), content, StandardOpenOption.CREATE_NEW);
            return "/uploads/image-jobs/" + jobId + "/outputs/" + filename;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存生成图片失败");
        }
    }
}
