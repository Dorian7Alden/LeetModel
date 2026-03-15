<template>

<div
class="markdown"
v-html="html"
/>

</template>

<script setup>

import { computed } from "vue"
import { marked } from "marked"

const props = defineProps({
content:String
})

const html = computed(()=>marked(props.content))

</script>

<style scoped>

.markdown{
line-height:1.7;
}

</style>