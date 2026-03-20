import { defineStore } from "pinia";

export const useUserStore = defineStore("user", {
  state: () => ({
    token: localStorage.getItem("token") || "",
    username: "",
  }),
  getters: {
    isLogin: (state) => !!state.token,
  },

  actions: {
    login(token, username) {
      this.token = token;
      this.username = username;

      localStorage.setItem("token", token);
    },

    logout() {
      this.token = "";
      this.username = "";

      localStorage.removeItem("token");
    },
  },
});
