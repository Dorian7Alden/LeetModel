export default [
  {
    path: "profile",
    name: "Profile",
    component: () => import("@/views/profile/ProfilePage.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "profile/analysis",
    name: "SkillAnalysis",
    component: () => import("@/views/profile/pages/SkillAnalysisPage.vue"),
  },
  {
    path: "profile/achievement",
    name: "Achievement",
    component: () => import("@/views/profile/pages/AchievementPage.vue"),
  },
  {
    path: "profile/history",
    name: "History",
    component: () => import("@/views/profile/pages/HistoryPage.vue"),
  },
  {
    path: "profile/settings",
    name: "Settings",
    component: () => import("@/views/profile/pages/SettingsPage.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "submission/:id",
    name: "SubmissionDetail",
    component: () => import("@/views/profile/pages/SubmissionDetailPage.vue"),
  },
]
