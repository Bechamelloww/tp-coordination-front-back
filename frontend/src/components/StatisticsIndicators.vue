<script setup lang="ts">
import {computed} from 'vue';
import {indicators,type StatisticsSummary} from '../statistics';
const props=defineProps<{summary:StatisticsSummary}>();
const items=computed(()=>indicators(props.summary));
</script>
<template>
 <dl class="stats-indicators">
  <div v-for="item in items" :key="item.label" class="stats-indicator"><dt>{{item.label}}</dt><dd>{{item.value}}</dd><dd class="stats-indicator-detail">{{item.detail}}</dd></div>
 </dl>
</template>
