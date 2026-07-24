package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import java.util.Set;

public final class ImageSizePolicy {
    private static final long QWEN_20_MIN_PIXELS = 512L * 512L;
    private static final long QWEN_20_MAX_PIXELS = 2048L * 2048L;
    private static final Set<String> QWEN_MAX_PLUS_SIZES = Set.of(
            "1664*928", "1472*1104", "1328*1328", "1104*1472", "928*1664");

    private ImageSizePolicy() {}

    public static String resolve(String modelName, String requestedSize, String genericDefault) {
        String size = requestedSize == null || requestedSize.isBlank()
                ? defaultFor(modelName, genericDefault)
                : normalize(requestedSize);
        validate(modelName, size);
        return size;
    }

    public static String defaultFor(String modelName, String genericDefault) {
        String model = normalizedModel(modelName);
        if (model.startsWith("qwen-image-2.0")) return "2048*2048";
        if (isMaxOrPlus(model)) return "1664*928";
        return normalize(genericDefault);
    }

    public static void validate(String modelName, String size) {
        String model = normalizedModel(modelName);
        if (model.startsWith("qwen-image-2.0")) {
            int[] dimensions = dimensions(size);
            long pixels = (long) dimensions[0] * dimensions[1];
            if (pixels < QWEN_20_MIN_PIXELS || pixels > QWEN_20_MAX_PIXELS) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "qwen-image-2.0 系列输出总像素必须在 512*512 至 2048*2048 之间");
            }
        } else if (isMaxOrPlus(model) && !QWEN_MAX_PLUS_SIZES.contains(normalize(size))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "qwen-image-max/plus 系列仅支持：1664*928、1472*1104、1328*1328、1104*1472、928*1664");
        }
    }

    public static String normalize(String size) {
        return size == null ? null : size.trim().toLowerCase().replace('x', '*');
    }

    private static int[] dimensions(String size) {
        String[] parts = normalize(size).split("\\*");
        if (parts.length != 2) throw invalidFormat();
        try {
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);
            if (width <= 0 || height <= 0) throw invalidFormat();
            return new int[]{width, height};
        } catch (NumberFormatException e) {
            throw invalidFormat();
        }
    }

    private static BusinessException invalidFormat() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "图片尺寸格式应为 宽*高，例如 2048*2048");
    }

    private static boolean isMaxOrPlus(String model) {
        return model.startsWith("qwen-image-max") || model.startsWith("qwen-image-plus");
    }

    private static String normalizedModel(String modelName) {
        return modelName == null ? "" : modelName.trim().toLowerCase();
    }
}
