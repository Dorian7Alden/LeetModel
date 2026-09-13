const privateRouteNameByStatus = {
  PREPARING: "TeamPreparing",
  IN_PROGRESS: "TeamPracticing",
  ENDED: "TeamEnded",
}

export default [
  {
    path: "/team",
    component: () => import("@/views/team/TeamLayout.vue"),
    meta: { requiresAuth: true },
    children: [
      {
        path: "",
        redirect: { name: "TeamPreparing" },
      },
      {
        path: "preparing/:teamId?",
        name: "TeamPreparing",
        component: () => import("@/views/team/pages/TeamPreparingPage.vue"),
        meta: { teamStatus: "PREPARING" },
      },
      {
        path: "practicing/:teamId?",
        name: "TeamPracticing",
        component: () => import("@/views/team/pages/TeamPracticingPage.vue"),
        meta: { teamStatus: "IN_PROGRESS" },
      },
      {
        path: "ended/:teamId?",
        name: "TeamEnded",
        component: () => import("@/views/team/pages/TeamEndedPage.vue"),
        meta: { teamStatus: "ENDED" },
      },
      {
        path: "square",
        name: "TeamSquare",
        component: () => import("@/views/team/pages/TeamSquarePage.vue"),
      },
      {
        path: "square/:id",
        name: "TeamSquareDetail",
        component: () => import("@/views/team/pages/TeamDetailPage.vue"),
      },
      {
        path: ":id",
        name: "TeamDetail",
        component: () => import("@/views/team/pages/TeamDetailPage.vue"),
        beforeEnter: (to) => {
          const routeName = privateRouteNameByStatus[String(to.query.fromStatus || "")]
          if (!routeName) return true
          return {
            name: routeName,
            params: { teamId: String(to.params.id) },
            replace: true,
          }
        },
      },
    ],
  },
]
