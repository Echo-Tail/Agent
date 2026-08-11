package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ProxySettingsDtos.UpdateRequest;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.ProxySetting;
import cafe.snails.ecomagents.repository.ProxySettingRepository;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProxySettingsServiceTest {
    private final ProxySettingRepository repository = mock(ProxySettingRepository.class);
    private final ProxySettingsService service = new ProxySettingsService(repository);

    @Test
    void updateShouldNormalizeAndPersistProxyUrl() {
        ProxySetting existing = setting(false, null);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(new UpdateRequest(true, " http://127.0.0.1:15236/ "), 7L);

        assertTrue(response.enabled());
        assertEquals("http://127.0.0.1:15236", response.proxyUrl());
        assertEquals(7L, response.updatedBy());
    }

    @Test
    void updateShouldRejectEnabledSettingWithoutProxyUrl() {
        assertThrows(BusinessException.class,
                () -> service.update(new UpdateRequest(true, " "), 7L));
    }

    @Test
    void updateShouldRejectNonHttpProxyUrl() {
        assertThrows(BusinessException.class,
                () -> service.update(new UpdateRequest(true, "https://127.0.0.1:15236"), 7L));
    }

    @Test
    void resolveProxyShouldReturnConfiguredHttpProxy() {
        when(repository.findById(1L)).thenReturn(Optional.of(setting(true, "http://127.0.0.1:15236")));

        Proxy proxy = service.resolveProxy();

        assertEquals(Proxy.Type.HTTP, proxy.type());
        InetSocketAddress address = (InetSocketAddress) proxy.address();
        assertEquals("127.0.0.1", address.getHostString());
        assertEquals(15236, address.getPort());
    }

    @Test
    void resolveProxyShouldUseDirectConnectionWhenDisabled() {
        when(repository.findById(1L)).thenReturn(Optional.of(setting(false, "http://127.0.0.1:15236")));

        assertSame(Proxy.NO_PROXY, service.resolveProxy());
    }

    @Test
    void createHttpClientShouldUseConfiguredProxyAndHttp11() {
        when(repository.findById(1L)).thenReturn(Optional.of(setting(true, "http://127.0.0.1:15236")));

        HttpClient client = service.createHttpClient(Duration.ofSeconds(15));

        assertEquals(HttpClient.Version.HTTP_1_1, client.version());
        InetSocketAddress address = (InetSocketAddress) client.proxy().orElseThrow()
                .select(java.net.URI.create("https://api.duckcoding.ai")).get(0).address();
        assertEquals("127.0.0.1", address.getHostString());
        assertEquals(15236, address.getPort());
    }

    private ProxySetting setting(boolean enabled, String proxyUrl) {
        return ProxySetting.builder().id(1L).enabled(enabled).proxyUrl(proxyUrl)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }
}
