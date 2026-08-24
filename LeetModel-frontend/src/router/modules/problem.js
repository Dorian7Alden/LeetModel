export default [
  {
    path: "problem",
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
        path: ":id",
        name: "ProblemDetail",
        component: () => import("@/views/problem/pages/ProblemDetailPage.vue"),
      },
    ],
  },
]
