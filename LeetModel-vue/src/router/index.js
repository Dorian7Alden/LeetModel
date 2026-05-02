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

      /* ================= 题库系统 ================= */

      {
        path: "problem",
        component: () => import("../modules/problem/view/Problem.vue"),
        children: [
          {
            path: "",
            redirect: "/problem/problemListPage",
          },
          {
            path: "problemListPage",
            name: "ProblemListPage",
            component: () =>
              import("../modules/problem/view/childview/ProblemListPage.vue"),
          },
          {
            path: "leetbook",
            name: "LeetBook",
            component: () =>
              import("../modules/problem/view/childview/LeetBook.vue"),
          },
          {
            path: ":id",
            name: "ProblemDetail",
            component: () =>
              import("../modules/problem/view/childview/ProblemDetail.vue"),
          },
          {
            path: "modeling",
            name: "ModelingTraining",
            component: () =>
              import("../modules/problem/view/childview/ModelingTraining.vue"),
          },

          {
            path: "paper",
            name: "PaperTraining",
            component: () =>
              import("../modules/problem/view/childview/PaperTraining.vue"),
          },

          {
            path: "coding",
            name: "CodingTraining",
            component: () =>
              import("../modules/problem/view/childview/CodingTraining.vue"),
          },
        ],
      },
      /* ================= 赛事系统 ================= */

      {
        path: "contest",
        name: "Contest",
        component: () => import("../modules/contest/view/Contest.vue"),
      },

      {
        path: "contest/id",
        name: "Contest/id",
        component: () =>
          import("../modules/contest/view/childview/ContestDetail.vue"),
      },
      {
        path: "contest",
        name: "Contest",
        component: () => import("../modules/contest/view/Contest.vue"),
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
      {
        path: "/forgot-password",
        component: () => import("../modules/user/ForgotPassword.vue"),
      },
      /* ================= 页脚关于 ================= */
      {
        path: "about",
        name: "About",
        component: () => import("../modules/about/About.vue"),
      },
      {
        path: "help",
        name: "Help",
        component: () => import("../modules/about/Help.vue"),
      },
      {
        path: "contact",
        name: "Contact",
        component: () => import("../modules/about/Contact.vue"),
      },
    ],
  },

  /* ================= 404 ================= */

  {
    path: "/:pathMatch(.*)*",
    name: "Notfound",
    component: () => import("../modules/Notfound.vue"),
  },

  /* =================后台管理页面================= */
  {
    path: '/admin',
    component: () => import("../modules/admin/layout/Layout.vue"),
    redirect: '/admin/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        meta: { title: '首页概览', icon: 'House' },
        children: [
          {
            path: 'dashboard',
            name: 'AdminDashboard',
            component: () => import('../modules/admin/views/dashboard/index.vue'),
            meta: { title: '首页概览' }
          }
        ]
      },
      {
        path: 'problem',
        meta: { title: '题目管理', icon: 'Document' },
        children: [
          {
            path: 'list',
            name: 'AdminProblemList',
            component: () => import('../modules/admin/views/problem/List.vue'),
            meta: { title: '题目管理' }
          }
        ]
      },
      {
        path: 'submission',
        meta: { title: '作品管理', icon: 'UploadFilled' },
        children: [
          {
            path: 'list',
            name: 'AdminSubmissionList',
            component: () => import('../modules/admin/views/submission/List.vue'),
            meta: { title: '作品管理' }
          }
        ]
      },
      {
        path: 'tag',
        meta: { title: '标签管理', icon: 'CollectionTag' },
        children: [
          {
            path: 'list',
            name: 'AdminTagList',
            component: () => import('../modules/admin/views/tag/List.vue'),
            meta: { title: '标签管理' }
          }
        ]
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem("token");
  const role = localStorage.getItem("role") || "";

  if (to.meta.requiresAuth && !token) {
    next("/login");
  } else if (to.path.startsWith("/admin") && role !== "admin") {
    next("/");
  } else {
    next();
  }
});

export default router;
