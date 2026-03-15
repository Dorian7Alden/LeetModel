<template>

<div class="discussion">

<h3>讨论区</h3>

<el-card>

<div
v-for="c in comments"
:key="c.id"
class="comment"
>

<b>{{c.user}}</b>

<p>{{c.content}}</p>

</div>

<el-divider />

<el-input
v-model="newComment"
type="textarea"
placeholder="发表评论"
/>

<el-button
type="primary"
style="margin-top:10px"
@click="addComment"
>

发表评论

</el-button>

</el-card>

</div>

</template>

<script setup>

import { ref } from 'vue'

const comments = ref([
{
id:1,
user:"student01",
content:"这个题可以用遗传算法"
},
{
id:2,
user:"modeler",
content:"我觉得蚁群算法更合适"
}
])

const newComment = ref('')

function addComment(){

comments.value.push({
id:Date.now(),
user:"当前用户",
content:newComment.value
})

newComment.value=''

}

</script>

<style scoped>

.discussion{
margin-top:40px;
}

.comment{
margin-bottom:15px;
}

</style>