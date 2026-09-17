export default [
  {
    path: "/ranking",
    name: "Ranking",
    component: () => import("@/views/ranking/RankingPage.vue"),
  },
  {
    path: "/assistant",
    name: "AiAssistant",
    component: () => import("@/views/assistant/AiAssistantPage.vue"),
    meta: { requiresAuth: true },
  },
];
