import { createRouter, createWebHistory } from "vue-router";
import AppLayout from "@/components/layout/AppLayout.vue";

import homeRoutes from "./modules/home";
import problemRoutes from "./modules/problem";
import contestRoutes from "./modules/contest";
import communityRoutes from "./modules/community";
import teamRoutes from "./modules/team";
import profileRoutes from "./modules/profile";
import authRoutes from "./modules/auth";
import aboutRoutes from "./modules/about";
import adminRoutes from "./modules/admin";

const routes = [
  {
    path: "/",
    component: AppLayout,
    children: [
      ...homeRoutes,
      ...problemRoutes,
      ...contestRoutes,
      ...communityRoutes,
      ...teamRoutes,
      ...profileRoutes,
      ...authRoutes,
      ...aboutRoutes,
    ],
  },

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
  const token = localStorage.getItem("token");
  const role = localStorage.getItem("role") || "";

  if (to.meta.requiresAuth && !token) {
    next("/login");
  } else if (to.path.startsWith("/admin") && role !== "ADMIN" && role !== "SUPER_ADMIN") {
    next("/");
  } else {
    next();
  }
});

export default router;
