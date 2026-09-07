import { createRouter, createWebHistory } from "vue-router";
import AppLayout from "@/components/layout/AppLayout.vue";

import officialRoutes from "./modules/official";
import homeRoutes from "./modules/home";
import problemRoutes from "./modules/problem";
import teamRoutes from "./modules/team";
import profileRoutes from "./modules/profile";
import authRoutes from "./modules/auth";
import aboutRoutes from "./modules/about";
import featureRoutes from "./modules/features";
import adminRoutes from "./modules/admin";
import { useUserStore } from "@/store/user";

const routes = [
  // 官网独立页：独占根路径，不经过 AppLayout
  ...officialRoutes,

  // 应用内页面：共享 AppLayout（含顶栏与全局底栏）
  {
    path: "/",
    component: AppLayout,
    children: [
      ...homeRoutes,
      ...problemRoutes,
      ...teamRoutes,
      ...profileRoutes,
      ...aboutRoutes,
      ...featureRoutes,
    ],
  },

  ...authRoutes,

  {
    path: "/:pathMatch(.*)*",
    name: "Notfound",
    component: () => import("@/views/NotFoundPage.vue"),
  },

  ...adminRoutes,
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to, from, next) => {
  const userStore = useUserStore();

  if (to.meta.requiresAuth && !userStore.isLogin) {
    next("/login");
  } else if (to.meta.guestOnly && userStore.isLogin) {
    next("/home");
  } else if (to.path.startsWith("/admin") && !userStore.isAdmin) {
    next("/home");
  } else {
    next();
  }
});

export default router;
