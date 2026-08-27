import request from "./request";

export function createConversation(title) {
  return request({ url: "/assistant/conversations", method: "post", data: { title } });
}

export function listConversations() {
  return request({ url: "/assistant/conversations", method: "get" });
}

export function getConversation(conversationId) {
  return request({ url: `/assistant/conversations/${conversationId}`, method: "get" });
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
