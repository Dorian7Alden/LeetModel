export default [
  {
    path: "/login",
    name: "Login",
    meta: { guestOnly: true },
    component: () => import("@/views/auth/LoginPage.vue"),
  },
  {
    path: "/register",
    name: "Register",
    meta: { guestOnly: true },
    component: () => import("@/views/auth/RegisterPage.vue"),
  },
]
