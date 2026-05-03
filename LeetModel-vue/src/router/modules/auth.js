export default [
  {
    path: "login",
    name: "Login",
    component: () => import("@/views/auth/LoginPage.vue"),
  },
  {
    path: "register",
    name: "Register",
    component: () => import("@/views/auth/RegisterPage.vue"),
  },
  {
    path: "forgot-password",
    component: () => import("@/views/auth/ForgotPasswordPage.vue"),
  },
]
