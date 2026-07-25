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
        path: "leetbook",
        name: "LeetBook",
        component: () => import("@/views/problem/pages/LeetBookPage.vue"),
      },
      {
        path: ":id",
        name: "ProblemDetail",
        component: () => import("@/views/problem/pages/ProblemDetailPage.vue"),
      },
      {
        path: "modeling",
        name: "ModelingTraining",
        component: () => import("@/views/problem/pages/ModelingTrainingPage.vue"),
      },
      {
        path: "paper",
        name: "PaperTraining",
        component: () => import("@/views/problem/pages/PaperTrainingPage.vue"),
      },
      {
        path: "coding",
        name: "CodingTraining",
        component: () => import("@/views/problem/pages/CodingTrainingPage.vue"),
      },
    ],
  },
]
