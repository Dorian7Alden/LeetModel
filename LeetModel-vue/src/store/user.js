import { defineStore } from "pinia";
import { logout as logoutApi } from "@/api/user";

export const useUserStore = defineStore("user", {
  state: () => ({
    token: localStorage.getItem("token") || "",
    username: "",
    email: "",
    role: "",
  }),

  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => state.role === "admin",
  },

  actions: {
    login(token, username, email, role) {
      this.token = token;
      this.username = username;
      this.email = email;
      this.role = role;

      localStorage.setItem("token", token);
    },

    async logout() {
      try {
        await logoutApi();
      } catch (err) {
        console.log("退出接口异常", err);
      } finally {
        this.token = "";
        this.username = "";
        this.email = "";
        this.role = "";

        localStorage.removeItem("token");
      }
    },
  },
});
