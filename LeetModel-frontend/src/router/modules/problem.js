export default [
  {
    path: "/problem",
    component: () => import("@/views/problem/ProblemLayout.vue"),
    children: [
      {
        path: "",
        redirect: "/problem/problemListPage",
      },
      {
        path: "problemListPage",
        name: "ProblemListPage",
        component: () => import("@/views/problem/pages/ProblemListPage.vue"),
      },
      {
        path: "contest/:contestId",
        name: "ContestProblemPage",
        component: () => import("@/views/problem/pages/ContestProblemPage.vue"),
      },
      {
        path: "type/:typeId",
        name: "TypeProblemPage",
        component: () => import("@/views/problem/pages/TypeProblemPage.vue"),
      },
      {
        path: ":id",
        name: "ProblemDetail",
        component: () => import("@/views/problem/pages/ProblemDetailPage.vue"),
      },
    ],
  },
]
