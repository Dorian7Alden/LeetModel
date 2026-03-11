<script setup>
import { ref } from 'vue'
const a = ref(0)
    function text(){
alert('这是主要按钮')
    }
    function text1(){
      alert('这是成功按钮')
    }
    
    function count (){
      a.value++
    }


</script>

<template>
  <div>
    <h1>Element Plus 测试</h1>
    <el-button type="primary" @click="text">主要按钮</el-button>
    <el-button type="success" @click="text1">成功按钮</el-button>
    <el-button type="warning">警告按钮</el-button>
    <h1>{{ a }}</h1>
    <el-button type="danger" @click="count">累加按钮</el-button>
  </div>
</template>

<style scoped>

</style>
