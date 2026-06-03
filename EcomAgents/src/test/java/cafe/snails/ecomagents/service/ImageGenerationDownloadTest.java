package cafe.snails.ecomagents.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 PackyAPI 外部资源下载接口的连通性。
 * 用于确认 HttpURLConnection + Chrome User-Agent 能成功下载图片，
 * 解决 CloudFront SSL 重新协商 + User-Agent 白名单问题。
 */
class ImageGenerationDownloadTest {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationDownloadTest.class);
    private static final String TEST_URL = "https://external-resources.packyapi.com/images/2026-06-03/905aa40a-1960-4686-8ba1-df2bea7fd6b5.png";
    private static final String CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";

    @Test
    void downloadImage_withChromeUA_shouldSucceed() throws Exception {
        // 模拟 ImageGenerationService.downloadImage() 的 HttpURLConnection 逻辑
        URL url = new URL(TEST_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", CHROME_UA);
        conn.setRequestProperty("Accept", "*/*");
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        int statusCode = conn.getResponseCode();
        log.info("HTTP status: {}", statusCode);
        assertEquals(200, statusCode, "下载接口应返回 200 OK");

        byte[] imageBytes = conn.getInputStream().readAllBytes();
        conn.disconnect();

        log.info("Downloaded {} bytes", imageBytes.length);
        assertNotNull(imageBytes, "响应体不应为 null");
        assertTrue(imageBytes.length > 0, "响应体不应为空");
        assertTrue(imageBytes.length > 100_000, "图片应大于 100KB（实际: " + imageBytes.length + " bytes）");

        // 验证 PNG 文件头
        assertEquals((byte) 0x89, imageBytes[0], "PNG 文件头第1字节应为 0x89");
        assertEquals((byte) 0x50, imageBytes[1], "PNG 文件头第2字节应为 'P'");
        assertEquals((byte) 0x4E, imageBytes[2], "PNG 文件头第3字节应为 'N'");
        assertEquals((byte) 0x47, imageBytes[3], "PNG 文件头第4字节应为 'G'");
    }

    @Test
    void downloadImage_withoutUA_shouldLogStatus() throws Exception {
        // 仅记录不带 UA 时的状态码（CloudFront 策略可能变化）
        URL url = new URL(TEST_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);

        int statusCode = conn.getResponseCode();
        log.info("HTTP status without User-Agent: {} (accept all, informational)", statusCode);
        // 非断言，仅记录；实际下载依赖 Chrome UA
        conn.disconnect();
    }

    @Test
    void downloadImage_withCurlUA_shouldSucceed() throws Exception {
        // 验证 curl User-Agent 也有效（辅助参考）
        URL url = new URL(TEST_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "curl/8.19.0");
        conn.setRequestProperty("Accept", "*/*");
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        int statusCode = conn.getResponseCode();
        assertEquals(200, statusCode, "curl User-Agent 也应返回 200");

        byte[] imageBytes = conn.getInputStream().readAllBytes();
        conn.disconnect();
        assertTrue(imageBytes.length > 100_000, "图片应大于 100KB");
        log.info("curl UA download: {} bytes", imageBytes.length);
    }
}
