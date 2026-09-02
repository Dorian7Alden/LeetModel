package com.leetmodel.common.api.feign;

import feign.Capability;
import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.CarrierItemRef;
import org.apache.skywalking.apm.toolkit.trace.ContextCarrierRef;
import org.apache.skywalking.apm.toolkit.trace.SpanRef;
import org.apache.skywalking.apm.toolkit.trace.Tracer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * 补齐 Agent 9.7 尚未覆盖的 OpenFeign 4.1 / Feign 13 客户端 Trace。
 *
 * <p>每次物理 HTTP 尝试创建一个有界 Exit Span，并通过官方 Toolkit Carrier
 * 注入 SW8。操作名只使用 Feign 契约方法，不使用已解析 URL、参数或请求体。</p>
 */
@Component
public final class SkyWalkingFeignCapability implements Capability {

    @Override
    public Client enrich(Client client) {
        return new TracingClient(client);
    }

    static final class TracingClient implements Client {
        private final Client delegate;

        private TracingClient(Client delegate) {
            this.delegate = delegate;
        }

        @Override
        public Response execute(Request request, Request.Options options) throws IOException {
            ContextCarrierRef carrier = new ContextCarrierRef();
            SpanRef span = Tracer.createExitSpan(operationName(request), carrier, peer(request));
            span.tag("rpc.system", "feign");
            span.tag("http.method", request.httpMethod().name());
            inject(request, carrier);
            try {
                Response response = delegate.execute(request, options);
                span.tag("http.status_code", Integer.toString(response.status()));
                if (response.status() >= 400) ActiveSpan.error();
                return response;
            } catch (IOException | RuntimeException exception) {
                // 不把异常 message、URL 或请求内容写入 Span。
                span.tag("error.kind", "transport");
                ActiveSpan.error();
                throw exception;
            } finally {
                Tracer.stopSpan();
            }
        }

        private static void inject(Request request, ContextCarrierRef carrier) {
            CarrierItemRef item = carrier.items();
            while (item.hasNext()) {
                item = item.next();
                String key = item.getHeadKey();
                String value = item.getHeadValue();
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    request.header(key, value);
                }
            }
        }

        static String operationName(Request request) {
            if (request.requestTemplate() != null
                    && request.requestTemplate().methodMetadata() != null) {
                return "Feign/" + request.requestTemplate().methodMetadata().configKey();
            }
            return "Feign/" + request.httpMethod().name();
        }

        private static String peer(Request request) {
            try {
                URI uri = URI.create(request.url());
                int port = uri.getPort();
                return port < 0 ? uri.getHost() : uri.getHost() + ":" + port;
            } catch (RuntimeException ignored) {
                return "unknown-peer";
            }
        }
    }
}
