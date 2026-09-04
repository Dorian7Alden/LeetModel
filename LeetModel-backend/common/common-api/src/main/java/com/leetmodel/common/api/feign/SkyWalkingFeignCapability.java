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
 * OpenFeign 客户端 SkyWalking APM 链路增强能力组件。
 *
 * <p>为 Feign 物理调用创建有界 Exit Span 并注入 SW8 上下文，操作名采用低基数契约方法签名，
 * 隔离原始 URL 动态参数与异常明文。</p>
 */
@Component
public final class SkyWalkingFeignCapability implements Capability {

    /**
     * 包装 Feign 客户端以赋予分布式追踪链路注入能力。
     *
     * @param client 原始 Feign HTTP Client
     * @return 增强了 SkyWalking Span 追踪的 TracingClient 代理实例
     */
    @Override
    public Client enrich(Client client) {
        return new TracingClient(client);
    }

    /**
     * 负责维护 Exit Span 生命周期与 SW8 上下文注入的 Feign Client 包装类。
     */
    static final class TracingClient implements Client {
        private final Client delegate;

        private TracingClient(Client delegate) {
            this.delegate = delegate;
        }

        /**
         * 执行带分布式链路追踪的 HTTP 调用。
         *
         * @param request 待发送的请求对象
         * @param options 超时与重试选项
         * @return 远程响应对象
         * @throws IOException 网络通信异常
         */
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

        /**
         * 将 ContextCarrier 中的追踪头注入 Feign 请求头中。
         *
         * @param request 待发送的请求对象
         * @param carrier SkyWalking 上下文载体
         */
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

        /**
         * 提取低基数的标准操作名称。
         *
         * @param request 待发送的请求对象
         * @return 格式为 Feign/MethodSignature 的低基数操作名
         */
        static String operationName(Request request) {
            if (request.requestTemplate() != null
                    && request.requestTemplate().methodMetadata() != null) {
                return "Feign/" + request.requestTemplate().methodMetadata().configKey();
            }
            return "Feign/" + request.httpMethod().name();
        }

        /**
         * 提取目标服务的对端主机与端口标识。
         *
         * @param request 待发送的请求对象
         * @return 目标主机与端口字符串
         */
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
