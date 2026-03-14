import { defineStore } from "pinia"

export const useUserStore = defineStore("user",{

state:()=>({
userInfo:null
}),

actions:{
setUser(user){
this.userInfo=user
}
}

})