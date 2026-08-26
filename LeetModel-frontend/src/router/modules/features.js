export default [
  {
    path: "ranking",
    name: "Ranking",
    component: () => import("@/views/ranking/RankingPage.vue"),
  },
  {
    path: "suggestion",
    name: "Suggestion",
    component: () => import("@/views/suggestion/SuggestionPage.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "assistant",
    name: "Assistant",
    component: () => import("@/views/assistant/AssistantPage.vue"),
    meta: { requiresAuth: true },
  },
];
