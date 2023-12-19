package sandipchitale.universalscgmvcfn;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
            // Return default one
            return null;
        } else {
            Duration connectionTimeout = connectTimeoutMillisString == null ? null : Duration.ofMillis(Long.parseLong(connectTimeoutMillisString));
            Duration readTimeout = readTimeoutMillisString == null ? null : Duration.ofMillis(Long.parseLong(readTimeoutMillisString));

            RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
            if (connectionTimeout != null) {
                if (connectionTimeout.equals(Duration.ZERO)) {
                    // 0 indicates infinite connect	 timeout
                    restTemplateBuilder = restTemplateBuilder.setConnectTimeout(Duration.ofMillis(Long.MAX_VALUE));
                } else {
                    restTemplateBuilder = restTemplateBuilder.setConnectTimeout(connectionTimeout);
                }
            }
            if (readTimeout != null) {
                if (readTimeout.equals(Duration.ZERO)) {
                    // 0 indicates infinite read timeout
                    restTemplateBuilder = restTemplateBuilder.setReadTimeout(Duration.ofMillis(Long.MAX_VALUE));
                } else {
                    restTemplateBuilder = restTemplateBuilder.setReadTimeout(readTimeout);
                }
            }

            SslBundle sslBundle = null;
            if (StringUtils.hasText(gatewayMvcProperties.getHttpClient().getSslBundle())) {
                sslBundle = sslBundles.getBundle(gatewayMvcProperties.getHttpClient().getSslBundle());
            }
            if (sslBundle != null) {
                restTemplateBuilder = restTemplateBuilder.setSslBundle(sslBundle);
            }

            return RestClient.create(restTemplateBuilder.build());
        }
    }
}