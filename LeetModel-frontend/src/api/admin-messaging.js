import request from "./request";

export function getMessagingOverview() {
  return request({ url: "/admin/messaging/overview", method: "get" });
}

export function listMessagingOutbox(service, params = {}) {
  return request({ url: `/admin/messaging/services/${service}/outbox`, method: "get", params });
}

export function listMessagingInbox(service, params = {}) {
  return request({ url: `/admin/messaging/services/${service}/inbox`, method: "get", params });
}

export function getMessagingTrace(traceId) {
  return request({ url: `/admin/messaging/traces/${encodeURIComponent(traceId)}`, method: "get" });
}

export function listMessagingDeadLetters(service) {
  return request({ url: `/admin/messaging/services/${service}/dlq`, method: "get" });
}

export function replayMessagingDeadLetters(service, consumerGroup, eventIds, reason) {
  return request({
    url: `/admin/messaging/services/${service}/dlq/replay`,
    method: "post",
    data: { consumerGroup, eventIds, reason },
  });
}

export function replayMessagingOutbox(service, eventIds, reason) {
  return request({
    url: `/admin/messaging/services/${service}/outbox/replay`,
    method: "post",
    data: { eventIds, reason },
  });
}

export function setMessagingConsumerPaused(service, consumerGroup, paused) {
  return request({
    url: `/admin/messaging/services/${service}/consumers/${encodeURIComponent(consumerGroup)}/${paused ? "pause" : "resume"}`,
    method: "post",
  });
}
