<script setup>

    function text(){
alert('这是主要按钮')
    }


</script>

<template>
  <div>
    <h1>Element Plus 测试</h1>
    <el-button type="primary" @click="text">主要按钮</el-button>
    <el-button type="success">成功按钮</el-button>
    <el-button type="warning">警告按钮</el-button>
    <el-button type="danger">危险按钮</el-button>
  </div>
</template>

<style scoped>

</style>
