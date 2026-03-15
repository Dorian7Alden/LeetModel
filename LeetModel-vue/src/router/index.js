import { createRouter, createWebHistory } from "vue-router";
import AppLayout from "../components/layout/AppLayout.vue";

const routes = [
  {
    path: "/",
    component: AppLayout,
    children: [
      /* ================= 首页模块 ================= */

      {
        path: "",
        name: "Home",
        component: () => import("../modules/home/view/Home.vue"),
      },

      /* ================= 训练系统 ================= */

      {
        path: "training",
        name: "Training",
        component: () => import("../modules/training/view/Training.vue"),

        children: [
          {
            path: "modeling",
            name: "ModelingTraining",
            component: () =>
              import("../modules/training/components/homepage/ModelingTraining.vue"),
          },

          {
            path: "paper",
            name: "PaperTraining",
            component: () =>
              import("../modules/training/components/homepage/PaperTraining.vue"),
          },

          {
            path: "coding",
            name: "CodingTraining",
            component: () =>
              import("../modules/training/components/homepage/CodingTraining.vue"),
          },
        ],
      },

      /* ================= 题库系统 ================= */

      {
        path: "problem",
        name: "Problem",
        component: () => import("../modules/problem/view/Problem.vue"),
      },

      {
        path: "problemHeader",
        name: "ProblemHeader",
        component: () =>
          import("../modules/problem/components/homepage/ProblemHeader.vue"),
      },

      {
        path: "problem/list",
        name: "ProblemList",
        component: () =>
          import("../modules/problem/components/homepage/ProblemList.vue"),
      },

      {
        path: "problem/:id",
        name: "ProblemDetail",
        component: () =>
          import("../modules/problem/view/childview/ProblemDetail.vue"),
      },

      {
        path: "problem/discussion",
        name: "ProblemDiscussion",
        component: () =>
          import("../modules/problem/components/subpage/ProblemDiscussion.vue"),
      },

      /* ================= 赛事系统 ================= */

      {
        path: "contest",
        name: "ContestList",
        component: () => import("../modules/contest/view/Contest.vue"),
      },

      {
        path: "contest/:id",
        name: "ContestDetail",
        component: () =>
          import("../modules/contest/view/childview/ContestDetail.vue"),
      },

      {
        path: "contest/rank",
        name: "ContestRank",
        component: () =>
          import("../modules/contest/components/subpage/ContestRank.vue"),
      },

      /* ================= 社区系统 ================= */

      {
        path: "community",
        name: "Community",
        component: () => import("../modules/community/view/Community.vue"),
      },

      {
        path: "post/create",
        name: "CreatePost",
        component: () =>
          import("../modules/community/view/childview/CreatePost.vue"),
      },
      {
        path: "post/:id",
        name: "PostDetail",
        component: () =>
          import("../modules/community/view/childview/PostDetail.vue"),
      },

      /* ================= 组队系统 ================= */

      {
        path: "team",
        name: "TeamList",
        component: () => import("../modules/team/view/Team.vue"),
      },

      {
        path: "team/:id",
        name: "TeamDetail",
        component: () =>
          import("../modules/team/view/childview/TeamDetail.vue"),
      },

      /* ================= 成长系统 ================= */

      {
        path: "profile",
        name: "Profile",
        component: () => import("../modules/profile/view/Profile.vue"),
      },

      {
        path: "profile/analysis",
        name: "SkillAnalysis",
        component: () =>
          import("../modules/profile/view/childview/SkillAnalysis.vue"),
      },

      {
        path: "profile/achievement",
        name: "Achievement",
        component: () =>
          import("../modules/profile/view/childview/Achievement.vue"),
      },

      {
        path: "profile/history",
        name: "History",
        component: () =>
          import("../modules/profile/view/childview/History.vue"),
      },
      {
        path: "profile/settings",
        name: "settings",
        component: () =>
          import("../modules/profile/view/childview/Settings.vue"),
      },
      {
        path: "submission/:id",
        name: "SubmissionDetail",
        component: () =>
          import("../modules/profile/view/childview/SubmissionDetail.vue"),
      },

      /* ================= 用户系统 ================= */

      {
        path: "login",
        name: "Login",
        component: () => import("../modules/user/Login.vue"),
      },

      {
        path: "register",
        name: "Register",
        component: () => import("../modules/user/Register.vue"),
      },
    ],
  },

  /* ================= 404 ================= */

  {
    path: "/:pathMatch(.*)*",
    name: "Notfound",
    component: () => import("../modules/Notfound.vue"),
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
