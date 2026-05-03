export default [
  {
    path: "profile",
    name: "Profile",
    component: () => import("@/views/profile/ProfilePage.vue"),
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
  },
  {
    path: "submission/:id",
    name: "SubmissionDetail",
    component: () => import("@/views/profile/pages/SubmissionDetailPage.vue"),
  },
]
