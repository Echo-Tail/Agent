package cafe.snails.ecomagents.service.image.runtime.storage;

import cafe.snails.ecomagents.exception.*;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.net.*;

@Component
/** 安全下载图片供应商返回的远程图片。 */
public class ImageRemoteDownloader {
    private static final int MAX_BYTES = 25 * 1024 * 1024;

    public DownloadedImage download(String imageUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(imageUrl).toURL();
            if (!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol()))
                throw new BusinessException(ErrorCode.BAD_REQUEST, "供应商返回了不支持的图片地址");
            for (InetAddress address : InetAddress.getAllByName(url.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress())
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "供应商图片地址指向受限网络");
            }
            connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "EcomAgents-ImageRuntime/1.0");
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(60_000);
            if (connection.getResponseCode() != 200)
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "下载供应商图片失败");
            int declaredLength = connection.getContentLength();
            if (declaredLength > MAX_BYTES) throw new BusinessException(ErrorCode.BAD_REQUEST, "供应商图片超过大小限制");
            byte[] content;
            try (InputStream input = connection.getInputStream()) { content = input.readNBytes(MAX_BYTES + 1); }
            if (content.length == 0 || content.length > MAX_BYTES)
                throw new BusinessException(ErrorCode.BAD_REQUEST, "供应商图片为空或超过大小限制");
            return new DownloadedImage(content, detectMime(content));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "下载供应商图片失败");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String detectMime(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) return "image/jpeg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        throw new BusinessException(ErrorCode.BAD_REQUEST, "供应商返回的内容不是受支持的图片");
    }

    public record DownloadedImage(byte[] content, String mimeType) {}
}
