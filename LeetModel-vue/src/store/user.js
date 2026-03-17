import { defineStore } from "pinia";

export const useUserStore = defineStore("user", {
  state: () => ({
    token: localStorage.getItem("token") || "",
    username: "",
    isLogin: !!localStorage.getItem("token"),
  }),

  actions: {
    login(token, username) {
      this.token = token;
      this.username = username;
      this.isLogin = true;

      localStorage.setItem("token", token);
    },

    logout() {
      this.token = "";
      this.username = "";
      this.isLogin = false;

      localStorage.removeItem("token");
    },
  },
});
