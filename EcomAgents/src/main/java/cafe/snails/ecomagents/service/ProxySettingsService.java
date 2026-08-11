package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ProxySettingsDtos.*;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.ProxySetting;
import cafe.snails.ecomagents.repository.ProxySettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责系统出站代理配置、自动探测和连通性测试。
 */
@Service
@RequiredArgsConstructor
public class ProxySettingsService {
    private static final long SETTINGS_ID = 1L;
    private static final URI PROBE_URI = URI.create("https://api.duckcoding.ai/v1/models");
    private static final Pattern WINDOWS_LISTENER =
            Pattern.compile("^\\s*TCP\\s+(?:127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[::1])[:](\\d+)\\s+.*LISTENING.*$",
                    Pattern.CASE_INSENSITIVE);

    private final ProxySettingRepository repository;
    private static final ProxySelector DIRECT_PROXY_SELECTOR = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress socketAddress, IOException error) {
        }
    };

    /** 查询当前代理设置。 */
    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        return toResponse(repository.findById(SETTINGS_ID).orElseGet(this::defaults));
    }

    /** 更新当前代理设置。 */
    @Transactional
    public SettingsResponse update(UpdateRequest request, Long userId) {
        String normalized = normalize(request.proxyUrl());
        if (Boolean.TRUE.equals(request.enabled()) && normalized == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "启用代理时必须填写代理地址");
        }
        ProxySetting setting = repository.findById(SETTINGS_ID).orElseGet(this::defaults);
        setting.setEnabled(request.enabled());
        setting.setProxyUrl(normalized);
        setting.setUpdatedBy(userId);
        setting.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(setting));
    }

    /** 返回用于指定地址连接的代理；未启用时返回直连代理。 */
    @Transactional(readOnly = true)
    public Proxy resolveProxy() {
        ProxySetting setting = repository.findById(SETTINGS_ID).orElse(null);
        if (setting == null || !Boolean.TRUE.equals(setting.getEnabled())
                || setting.getProxyUrl() == null || setting.getProxyUrl().isBlank()) {
            return Proxy.NO_PROXY;
        }
        return toProxy(setting.getProxyUrl());
    }

    /** 使用当前代理配置打开网络连接。 */
    public URLConnection openConnection(URL url) throws IOException {
        return url.openConnection(resolveProxy());
    }

    /** 创建使用当前代理配置的 HTTP/1.1 客户端。 */
    public HttpClient createHttpClient(Duration connectTimeout) {
        Proxy proxy = resolveProxy();
        ProxySelector selector = proxy.address() instanceof InetSocketAddress address
                ? ProxySelector.of(address)
                : DIRECT_PROXY_SELECTOR;
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(selector)
                .build();
    }

    /** 探测环境变量、JVM、系统代理以及本机监听的 HTTP 代理。 */
    public DetectionResponse detect() {
        LinkedHashMap<String, String> candidates = new LinkedHashMap<>();
        addCandidate(candidates, currentConfiguredUrl(), "当前配置");
        addCandidate(candidates, propertyProxy("https"), "JVM HTTPS 代理");
        addCandidate(candidates, propertyProxy("http"), "JVM HTTP 代理");
        addCandidate(candidates, System.getenv("HTTPS_PROXY"), "HTTPS_PROXY 环境变量");
        addCandidate(candidates, System.getenv("HTTP_PROXY"), "HTTP_PROXY 环境变量");
        detectWithProxySelector(candidates);
        detectWindowsListeners(candidates);

        List<ProxyCandidate> results = candidates.entrySet().stream()
                .map(entry -> new ProxyCandidate(entry.getKey(), entry.getValue(), probe(entry.getKey()).success()))
                .toList();
        String suggestion = results.stream().filter(ProxyCandidate::reachable)
                .map(ProxyCandidate::proxyUrl).findFirst().orElse(null);
        return new DetectionResponse(suggestion != null, suggestion, results);
    }

    /** 测试指定代理或当前保存的代理是否能访问外部 HTTPS 接口。 */
    public TestResponse test(String requestedProxyUrl) {
        String url = normalize(requestedProxyUrl);
        if (url == null) url = currentConfiguredUrl();
        if (url == null) {
            return new TestResponse(false, "请先填写代理地址", null, 0);
        }
        return probe(url);
    }

    private TestResponse probe(String proxyUrl) {
        long startedAt = System.nanoTime();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) PROBE_URI.toURL().openConnection(toProxy(proxyUrl));
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36");
            connection.setConnectTimeout(3_000);
            connection.setReadTimeout(5_000);
            int status = connection.getResponseCode();
            String contentType = connection.getContentType();
            boolean success = status == 401 || status == 403
                    || (status >= 200 && status < 300
                    && contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json"));
            String message = success ? "代理连接正常，可访问图片供应商 API"
                    : "代理已连接，但目标接口返回异常状态 " + status;
            return new TestResponse(success, message, status, elapsedMs(startedAt));
        } catch (Exception e) {
            return new TestResponse(false, "代理连接失败：" + safeMessage(e), null, elapsedMs(startedAt));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void detectWithProxySelector(Map<String, String> candidates) {
        try {
            for (Proxy proxy : ProxySelector.getDefault().select(PROBE_URI)) {
                if (proxy.address() instanceof InetSocketAddress address) {
                    addCandidate(candidates, "http://" + address.getHostString() + ":" + address.getPort(),
                            "系统代理");
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void detectWindowsListeners(Map<String, String> candidates) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return;
        try {
            Process process = new ProcessBuilder("netstat", "-ano", "-p", "tcp")
                    .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int examined = 0;
                while ((line = reader.readLine()) != null && examined < 200) {
                    Matcher matcher = WINDOWS_LISTENER.matcher(line);
                    if (!matcher.matches()) continue;
                    int port = Integer.parseInt(matcher.group(1));
                    examined++;
                    String candidate = "http://127.0.0.1:" + port;
                    if (quickConnectProbe(port)) addCandidate(candidates, candidate, "本机 HTTP 代理探测");
                }
            }
            process.destroy();
        } catch (Exception ignored) {
        }
    }

    private boolean quickConnectProbe(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
            socket.setSoTimeout(500);
            OutputStream output = socket.getOutputStream();
            output.write(("CONNECT api.duckcoding.ai:443 HTTP/1.1\r\n"
                    + "Host: api.duckcoding.ai:443\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.flush();
            String status = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            return status != null && status.matches("HTTP/\\d(?:\\.\\d)? 200.*");
        } catch (Exception ignored) {
            return false;
        }
    }

    private Proxy toProxy(String proxyUrl) {
        try {
            URI uri = URI.create(normalize(proxyUrl));
            int port = uri.getPort();
            if (port <= 0) port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), port));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "代理地址格式不正确");
        }
    }

    private String normalize(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isBlank()) return null;
        String value = proxyUrl.trim().replaceAll("/+$", "");
        try {
            URI uri = URI.create(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "代理地址应为 http://主机:端口，例如 http://127.0.0.1:15236");
        }
    }

    private String propertyProxy(String scheme) {
        String host = System.getProperty(scheme + ".proxyHost");
        String port = System.getProperty(scheme + ".proxyPort");
        return host == null || host.isBlank() ? null
                : "http://" + host + (port == null || port.isBlank() ? "" : ":" + port);
    }

    private String currentConfiguredUrl() {
        return repository.findById(SETTINGS_ID).map(ProxySetting::getProxyUrl).orElse(null);
    }

    private void addCandidate(Map<String, String> candidates, String value, String source) {
        try {
            String normalized = normalize(value);
            if (normalized != null) candidates.putIfAbsent(normalized, source);
        } catch (BusinessException ignored) {
        }
    }

    private ProxySetting defaults() {
        return ProxySetting.builder().id(SETTINGS_ID).enabled(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private SettingsResponse toResponse(ProxySetting setting) {
        return new SettingsResponse(Boolean.TRUE.equals(setting.getEnabled()), setting.getProxyUrl(),
                setting.getUpdatedBy(), setting.getUpdatedAt());
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String safeMessage(Exception error) {
        return error instanceof SocketTimeoutException ? "连接超时" : error.getClass().getSimpleName();
    }
}
