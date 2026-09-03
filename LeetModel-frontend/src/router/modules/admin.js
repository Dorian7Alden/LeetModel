const legacyRedirect = (path, view) => ({
  path,
  redirect: { path: `/admin/${view.domain}`, query: { view: view.key } },
  meta: { hidden: true },
});

export default [
  {
    path: "/admin",
    component: () => import("@/views/admin/AdminLayout.vue"),
    redirect: "/admin/dashboard",
    meta: { requiresAuth: true },
    children: [
      {
        path: "dashboard",
        name: "AdminDashboard",
        component: () => import("@/views/admin/pages/DashboardPage.vue"),
        meta: { title: "运行概览", navTitle: "概览", description: "平台业务与 AI 运行状态", icon: "DataBoard" },
      },
      {
        path: "access",
        name: "AdminAccess",
        component: () => import("@/views/admin/pages/AccessControlPage.vue"),
        meta: { title: "访问控制", description: "用户、角色与权限策略", icon: "Lock" },
      },
      {
        path: "content",
        name: "AdminContent",
        component: () => import("@/views/admin/pages/ContentHubPage.vue"),
        meta: { title: "内容中心", description: "题目、标签与赛事内容", icon: "Reading" },
      },
      {
        path: "operations",
        name: "AdminOperations",
        component: () => import("@/views/admin/pages/OperationsHubPage.vue"),
        meta: { title: "业务运营", description: "组队到评审排行的业务链", icon: "TrendCharts" },
      },
      {
        path: "ai",
        name: "AdminAiCenter",
        component: () => import("@/views/admin/pages/AiCenterPage.vue"),
        meta: { title: "AI 中枢", description: "调用、评价与生产版本治理", icon: "Cpu" },
      },
      {
        path: "audit",
        name: "AdminAudit",
        component: () => import("@/views/admin/pages/AuditPage.vue"),
        meta: { title: "操作审计", navTitle: "审计", description: "高风险操作的只读时间线", icon: "DocumentChecked" },
      },
      legacyRedirect("users/list", { domain: "access", key: "users" }),
      legacyRedirect("role/list", { domain: "access", key: "roles" }),
      legacyRedirect("permission/list", { domain: "access", key: "permissions" }),
      legacyRedirect("auth/index", { domain: "access", key: "authorization" }),
      legacyRedirect("problem/list", { domain: "content", key: "problems" }),
      legacyRedirect("tags/list", { domain: "content", key: "tags" }),
      legacyRedirect("contest/list", { domain: "content", key: "contests" }),
      legacyRedirect("submissions/list", { domain: "operations", key: "submissions" }),
      legacyRedirect("teams/list", { domain: "operations", key: "teams" }),
      legacyRedirect("reviews/list", { domain: "operations", key: "reviews" }),
      legacyRedirect("suggestions/list", { domain: "operations", key: "suggestions" }),
      legacyRedirect("rankings/list", { domain: "operations", key: "rankings" }),
      legacyRedirect("ai-calls/list", { domain: "ai", key: "calls" }),
      legacyRedirect("evaluations/list", { domain: "ai", key: "evaluations" }),
      legacyRedirect("production-workflows/list", { domain: "ai", key: "production" }),
    ],
  },
];
