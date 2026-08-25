export default [
  {
    path: "team",
    name: "MyTeams",
    component: () => import("@/views/team/TeamPage.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "team/square",
    name: "TeamSquare",
    component: () => import("@/views/team/pages/TeamSquarePage.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "team/square/:id",
    name: "TeamSquareDetail",
    component: () => import("@/views/team/pages/TeamDetailPage.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "team/:id",
    name: "TeamDetail",
    component: () => import("@/views/team/pages/TeamDetailPage.vue"),
    meta: { requiresAuth: true },
  },
]
