<template>

<div ref="chart" style="height:400px"></div>

</template>

<script setup>

import * as echarts from "echarts"
import { onMounted,ref } from "vue"

const chart = ref()

onMounted(()=>{

let myChart = echarts.init(chart.value)

myChart.setOption({

xAxis:{
type:"category",
data:["1月","2月","3月","4月"]
},

yAxis:{
type:"value"
},

series:[{
data:[10,20,30,40],
type:"line"
}]

})

})

</script>