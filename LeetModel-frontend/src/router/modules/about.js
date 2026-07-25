export default [
  {
    path: "about",
    name: "About",
    component: () => import("@/views/about/AboutPage.vue"),
  },
  {
    path: "help",
    name: "Help",
    component: () => import("@/views/about/HelpPage.vue"),
  },
  {
    path: "contact",
    name: "Contact",
    component: () => import("@/views/about/ContactPage.vue"),
  },
]
