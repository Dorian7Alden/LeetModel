export default [
  {
    path: "contest",
    name: "Contest",
    component: () => import("@/views/contest/ContestPage.vue"),
  },
  {
    path: "contest/:id",
    name: "ContestDetail",
    component: () => import("@/views/contest/pages/ContestDetailPage.vue"),
  },
]
