<template>

<div class="team-card" @click="goDetail">

  <h3>{{team.name}}</h3>

  <p class="members">
    当前人数：{{team.members}} / 3
  </p>

  <div class="missing">

    <span
      v-for="role in team.missing"
      :key="role"
      class="tag"
    >
      缺 {{role}}
    </span>

  </div>

</div>

</template>

<script setup>

import { useRouter } from "vue-router"

const props = defineProps({
  team:Object
})

const router = useRouter()

function goDetail(){
  router.push({
    name:"TeamDetail",
    params:{ id: props.team.id }
  })
}

</script>

<style scoped>

.team-card{

background:white;

padding:20px;

border-radius:8px;

box-shadow:0 2px 6px rgba(0,0,0,0.1);

cursor:pointer;

transition:0.2s;

}

.team-card:hover{
transform:translateY(-4px);
}

.members{
margin:10px 0;
color:#666;
}

.missing{
display:flex;
gap:10px;
}

.tag{
background:#f56c6c;
color:white;
padding:4px 10px;
border-radius:4px;
font-size:12px;
}

</style>