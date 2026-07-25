export default [
  {
    path: "team",
    name: "TeamList",
    component: () => import("@/views/team/TeamPage.vue"),
  },
  {
    path: "team/:id",
    name: "TeamDetail",
    component: () => import("@/views/team/pages/TeamDetailPage.vue"),
  },
]
