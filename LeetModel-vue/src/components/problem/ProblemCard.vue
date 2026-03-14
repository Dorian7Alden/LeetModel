<template>

<el-card class="card">

<h3>{{problem.title}}</h3>

<div class="tags">

<el-tag
v-for="tag in problem.tags"
:key="tag"
size="small"
>

{{tag}}

</el-tag>

</div>

<div class="footer">

难度：{{problem.difficulty}}

<el-button
type="primary"
size="small"
@click="goDetail"
>

进入

</el-button>

</div>

</el-card>

</template>

<script setup>

import { useRouter } from "vue-router"

const router = useRouter()

const props = defineProps({
problem:Object
})

function goDetail(){

router.push("/problem/"+props.problem.id)

}

</script>

<style scoped>

.card{
margin-bottom:20px;
}

.tags{
margin:10px 0;
}

.footer{
display:flex;
justify-content:space-between;
}

</style>