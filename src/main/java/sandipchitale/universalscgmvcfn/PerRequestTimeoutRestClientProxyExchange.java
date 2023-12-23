package sandipchitale.universalscgmvcfn;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.handler.RestClientProxyExchange;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class PerRequestTimeoutRestClientProxyExchange extends RestClientProxyExchange {

    static final String X_CONNECT_TIMEOUT_MILLIS = "X-CONNECT-TIMEOUT-MILLIS";
    static final String X_READ_TIMEOUT_MILLIS = "X-READ-TIMEOUT-MILLIS";

    private final RestClient.Builder restClientBuilder;
    private final GatewayMvcProperties gatewayMvcProperties;
    private final SslBundles sslBundles;

    // Cache
    private final Map<Long, RestClient> xTimeoutMillisToRestClientMap = new HashMap<>();
    private final Method superCopyBody;
    private final Method superDoExchange;

    public PerRequestTimeoutRestClientProxyExchange(RestClient.Builder restClientBuilder,
                                                    GatewayMvcProperties gatewayMvcProperties,
                                                    SslBundles sslBundles) {
        super(restClientBuilder.build());
        this.restClientBuilder = restClientBuilder;
        this.gatewayMvcProperties = gatewayMvcProperties;
        this.sslBundles = sslBundles;

        superCopyBody = ReflectionUtils.findMethod(RestClientProxyExchange.class, "copyBody", Request.class, OutputStream.class);
        if (superCopyBody != null) {
            ReflectionUtils.makeAccessible(superCopyBody);
        }

        superDoExchange = ReflectionUtils.findMethod(RestClientProxyExchange.class, "doExchange", Request.class, ClientHttpResponse.class);
        if (superDoExchange != null) {
            ReflectionUtils.makeAccessible(superDoExchange);
        }
    }

    @Override
    public ServerResponse exchange(Request request) {
        RestClient restClient = getRestClient(request.getServerRequest().servletRequest(),  gatewayMvcProperties, sslBundles);
        if (restClient == null) {
            return super.exchange(request);
        } else {
            try {
                return restClient
                        .method(request.getMethod())
                        .uri(request.getUri())
                        .headers((HttpHeaders httpHeaders) -> {
                            httpHeaders.putAll(request.getHeaders());
                        })
                        .body((OutputStream outputStream) -> {
                            copyBody(superCopyBody, request, outputStream);
                        })
                        .exchange((HttpRequest clientRequest, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse clientResponse) -> {
                                return doExchange(superDoExchange, request, clientResponse);
                        }, false);
            } catch (ResourceAccessException resourceAccessException) {
                if (resourceAccessException.getCause() instanceof SocketTimeoutException) {
                    return ServerResponse
                            .status(HttpStatus.GATEWAY_TIMEOUT)
                            .body(HttpStatus.GATEWAY_TIMEOUT.getReasonPhrase() + ": " + resourceAccessException.getMessage());
                } else {
                    throw resourceAccessException;
                }
            }
        }
    }

    // Try to use original implementation as much as possible
    private static void copyBody(Method superCopyBody,
                                 Request request,
                                 OutputStream outputStream) {
        ReflectionUtils.invokeMethod(superCopyBody,
                null,
                request,
                outputStream);
    }

    private static ServerResponse doExchange(Method superDoExchange,
                                             Request request,
                                             ClientHttpResponse clientResponse) {
        return (ServerResponse) ReflectionUtils.invokeMethod(superDoExchange,
                null,
                request,
                clientResponse);
    }

    private RestClient getRestClient(HttpServletRequest httpServletRequest, GatewayMvcProperties gatewayMvcProperties, SslBundles sslBundles) {
        String connectTimeoutMillisString = httpServletRequest.getHeader(X_CONNECT_TIMEOUT_MILLIS);
        String readTimeoutMillisString = httpServletRequest.getHeader(X_READ_TIMEOUT_MILLIS);
        if (connectTimeoutMillisString == null && readTimeoutMillisString == null) {
            // Return null so that default one will be used.
            return null;
        } else {
            Duration connectionTimeout = connectTimeoutMillisString == null ? null : Duration.ofMillis(Long.parseLong(connectTimeoutMillisString));
            Duration readTimeout = readTimeoutMillisString == null ? null : Duration.ofMillis(Long.parseLong(readTimeoutMillisString));

            ClientHttpRequestFactorySettings clientHttpRequestFactorySettings =
                    ClientHttpRequestFactorySettings.DEFAULTS;

            if (connectionTimeout != null) {
                clientHttpRequestFactorySettings = clientHttpRequestFactorySettings.withConnectTimeout(connectionTimeout);
            }

            if (readTimeout != null) {
                clientHttpRequestFactorySettings = clientHttpRequestFactorySettings.withReadTimeout(readTimeout);
            }

            SslBundle sslBundle = null;
            if (StringUtils.hasText(gatewayMvcProperties.getHttpClient().getSslBundle())) {
                sslBundle = sslBundles.getBundle(gatewayMvcProperties.getHttpClient().getSslBundle());
            }
            if (sslBundle != null) {
                clientHttpRequestFactorySettings = clientHttpRequestFactorySettings.withSslBundle(sslBundle);
            }

            return restClientBuilder.requestFactory(ClientHttpRequestFactories.get(clientHttpRequestFactorySettings)).build();
        }
    }
}