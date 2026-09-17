import request from "./request";

export function createConversation(title) {
  return request({ url: "/assistant/conversations", method: "post", data: { title } });
}

export function listConversations() {
  return request({ url: "/assistant/conversations", method: "get" });
}

export function getConversation(conversationId, params) {
  return request({ url: `/assistant/conversations/${conversationId}`, method: "get", params });
}

export function renameConversation(conversationId, title) {
  return request({
    url: `/assistant/conversations/${conversationId}/title`,
    method: "put",
    data: { title },
  });
}

export function deleteConversation(conversationId) {
  return request({
    url: `/assistant/conversations/${conversationId}`,
    method: "delete",
  });
}

export function sendMessage(conversationId, content, clientRequestId) {
  return request({
    url: `/assistant/conversations/${conversationId}/messages`,
    method: "post",
    data: { content, clientRequestId },
  });
}

export function retryMessage(messageId) {
  return request({ url: `/assistant/conversations/messages/${messageId}/retry`, method: "post" });
}

export function closeConversation(conversationId) {
  return request({ url: `/assistant/conversations/${conversationId}/close`, method: "post" });
}
