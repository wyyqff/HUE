<script setup lang="ts">
import { computed, ref } from 'vue'
import { MapPin, Search, Maximize2, RotateCw } from 'lucide-vue-next'
import { campusScenes, searchCampusScenes } from '@/config/campusScenes'
const keyword = ref('')
const submitted = ref('')
const selected = ref('')
const opened = ref(false)
const loading = ref(false)
const reloadKey = ref(0)
const panel = ref<HTMLElement | null>(null)
const matches = computed(() => searchCampusScenes(submitted.value))
function open() { if (!opened.value) { loading.value = true; opened.value = true } }
function search(value = keyword.value) { keyword.value = value.trim(); submitted.value = keyword.value; selected.value = ''; open() }
function select(name: string) { selected.value = name; open() }
function reload() { loading.value = true; reloadKey.value++ }
async function fullscreen() { try { if (document.fullscreenElement) await document.exitFullscreen(); else await panel.value?.requestFullscreen() } catch { /* Fullscreen may be disabled by the browser. */ } }
</script>

<template>
  <section id="campus-guide" class="campus-guide" aria-labelledby="guide-title">
    <div class="guide-heading"><div><p class="eyebrow">EXPLORE OUR CAMPUS</p><h2 id="guide-title">校园实景导览</h2><p>在这里看校园，为下一次面交选个熟悉的公共地点。</p></div><p class="address"><MapPin :size="16" /> 太极路19号 · 河北工程大学</p></div>
    <div class="guide-layout">
      <aside>
        <form class="guide-search" @submit.prevent="search()"><Search :size="17" /><input v-model="keyword" aria-label="搜索校园场景" placeholder="图书馆、食堂、教学楼…" maxlength="40" /><button type="submit">查找</button></form>
        <div class="popular"><button v-for="name in ['图书馆','餐厅','教学楼','精工湖','篮球场']" :key="name" @click="search(name)">{{ name }}</button></div>
        <p class="count" role="status">{{ submitted ? '找到 ' + matches.length + ' 个场景' : campusScenes.length + ' 个校园场景' }}</p>
        <div class="results"><button v-for="scene in matches" :key="scene.id" :class="{ selected: selected === scene.name }" @click="select(scene.name)"><MapPin :size="14" />{{ scene.name }}</button><p v-if="!matches.length">没有找到这个场景，试试建筑名称或清空搜索。</p></div>
        <p class="note">学校公开的 360° 实景全景，可旋转和缩放；不是自由漫游的三维建筑模型。</p>
      </aside>
      <div ref="panel" class="viewer-panel">
        <template v-if="opened">
          <div class="viewer-tools"><span>河北工程大学 · 全景校园</span><div><button @click="reload" aria-label="重新加载全景"><RotateCw :size="17" /></button><button @click="fullscreen" aria-label="全屏查看校园全景"><Maximize2 :size="17" /></button></div></div>
          <p v-if="selected" class="hint" role="status">目标：{{ selected }}。进入全景后，在画面内的场景列表选择该名称。学校设置了固定开场，外部搜索不能自动定位。</p>
          <p v-else class="hint">点击画面进入全景，在画面内切换场景；拖动旋转，滚轮缩放。</p>
          <p v-if="loading" class="loading" role="status">正在连接校园全景…</p>
          <iframe :key="reloadKey" src="https://www.720yun.com/t/2avktm1qs2m" title="河北工程大学校园全景" allow="fullscreen; gyroscope; accelerometer" allowfullscreen @load="loading = false" />
          <p class="connection-note">全景需要联网，若画面未显示，可点击右上角重新加载。</p>
        </template>
        <button v-else class="cover" @click="open"><img src="/campus/campus-evening-4k.jpg" alt="河北工程大学湖畔建筑高清实景" width="3839" height="1594" loading="lazy" decoding="async" /><span><strong>走进河工程</strong><em>打开校园全景 · 在当前页面查看</em></span></button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.campus-guide{padding:64px min(8%,110px);background:white;color:#183047;scroll-margin-top:100px}.guide-heading{display:flex;justify-content:space-between;align-items:end;gap:20px;margin-bottom:26px}.eyebrow{font-size:10px!important;letter-spacing:3px;color:#a82634!important;font-weight:700;margin-bottom:12px}h2{font-size:30px;font-weight:700;margin-bottom:12px}.guide-heading p{font-size:13px;color:#63788b;line-height:1.8}.address{display:flex;gap:6px;align-items:center;white-space:nowrap}.guide-layout{display:grid;grid-template-columns:280px minmax(0,1fr);gap:24px}.guide-layout aside{min-width:0}.guide-search{display:flex;align-items:center;gap:8px;border:1px solid #cddbe5;border-radius:10px;padding:7px 7px 7px 12px}.guide-search input{min-width:0;flex:1;outline:none;font-size:12px;background:transparent}.guide-search:focus-within{outline:2px solid #254b68;outline-offset:2px}.guide-search button{background:#173b58;color:white;border-radius:6px;padding:8px 10px;font-size:12px;white-space:nowrap}.popular{display:flex;gap:7px;flex-wrap:wrap;margin:12px 0}.popular button{background:#edf2f6;color:#254b68;border-radius:20px;padding:6px 10px;font-size:11px}.count{font-size:11px;color:#63788b;margin:16px 0 8px}.results{max-height:280px;overflow:auto;scrollbar-width:thin;display:flex;flex-direction:column;gap:3px}.results button{display:flex;gap:8px;align-items:center;text-align:left;padding:10px;border-radius:8px;font-size:12px}.results button:hover,.results button.selected{background:#edf2f6;color:#a82634}.note,.results p{font-size:11px;line-height:1.8;color:#63788b;margin-top:15px}.viewer-panel{height:510px;min-width:0;position:relative;overflow:hidden;border-radius:16px;background:#10283b;display:flex;flex-direction:column}.cover{position:relative;width:100%;height:100%;text-align:left}.cover img{display:block;width:100%;height:100%;object-fit:cover}.cover span{position:absolute;inset:0;background:linear-gradient(0deg,#10283bee,transparent 70%);color:#fff;display:flex;flex-direction:column;justify-content:end;gap:14px;padding:35px}.cover strong{font-size:27px;font-weight:500}.cover em{font-style:normal;font-size:12px;letter-spacing:1px}.viewer-tools{display:flex;align-items:center;justify-content:space-between;color:#fff;padding:12px 16px;font-size:12px;flex-shrink:0}.viewer-tools>div{display:flex;gap:15px}.viewer-tools button{padding:4px}.hint,.connection-note{font-size:11px;line-height:1.7;color:#d4dfe8;padding:0 16px 10px;flex-shrink:0}.connection-note{padding:8px 16px}iframe{border:0;display:block;width:100%;flex:1;min-height:0;background:#10283b}.loading{position:absolute;inset:110px 0 auto;text-align:center;color:#fff;font-size:12px;pointer-events:none}.viewer-panel:fullscreen{border-radius:0;width:100vw;height:100vh;background:#10283b}@media(max-width:850px){.campus-guide{padding:38px 24px}.guide-heading{display:block}.address{margin-top:10px}.guide-layout{grid-template-columns:1fr;gap:18px}.results{max-height:150px;display:grid;grid-template-columns:1fr 1fr}.note{margin-top:10px}.viewer-panel{height:460px}h2{font-size:24px}}
</style>
