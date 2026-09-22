<script setup lang="ts">
import { computed, ref } from 'vue'
import { MapPin, Search, ArrowUpRight, Maximize2, X } from 'lucide-vue-next'

const keyword = ref('')
const activeKeyword = ref('')
const showMap = ref(false)
const showPhoto = ref(false)
const photoDialog = ref<HTMLDialogElement | null>(null)
function openPhoto() { showPhoto.value = true; photoDialog.value?.showModal() }
const places = ['图书馆', '机械与装备工程学院', '工程实训中心', '食堂', '学生宿舍']
const mapUrl = computed(() => `https://uri.amap.com/search?keyword=${encodeURIComponent('河北工程大学 ' + activeKeyword.value)}&city=${encodeURIComponent('邯郸')}&view=map&src=hebeu-campus&callnative=0`)
function search(place = keyword.value) {
  activeKeyword.value = place.trim().slice(0, 80)
  keyword.value = activeKeyword.value
  showMap.value = true
}
</script>

<template>
  <section id="campus-guide" class="campus-guide" aria-labelledby="campus-guide-title">
    <div class="guide-copy">
      <p class="guide-eyebrow">EXPLORE OUR CAMPUS</p>
      <h2 id="campus-guide-title">走进河工程</h2>
      <p class="guide-description">从教室到图书馆，从一件好物到一次相遇。<br />在熟悉的校园里，找到下一站。</p>
      <p class="guide-address"><MapPin :size="17" /> 河北省邯郸经济技术开发区太极路19号</p>
      <form class="guide-search" @submit.prevent="search()">
        <label for="campus-search" class="sr-only">搜索河北工程大学校内地点</label>
        <Search :size="19" aria-hidden="true" />
        <input id="campus-search" v-model="keyword" maxlength="80" placeholder="搜索图书馆、食堂、教学楼…" />
        <button type="submit">搜索地图</button>
      </form>
      <div class="guide-places" aria-label="常用地点">
        <button v-for="place in places" :key="place" type="button" @click="search(place)">{{ place }}</button>
      </div>
      <div class="guide-links">
        <a :href="mapUrl" target="_blank" rel="noopener noreferrer">高德地图导航 <ArrowUpRight :size="16" /></a>
        <a href="https://www.720yun.com/t/2avktm1qs2m" target="_blank" rel="noopener noreferrer">校园全景 <ArrowUpRight :size="16" /></a>
        <a href="https://www.hebeu.edu.cn/" target="_blank" rel="noopener noreferrer">学校官网 <ArrowUpRight :size="16" /></a>
      </div>
    </div>
    <div class="guide-visual">
      <template v-if="showMap">
        <div class="map-caption"><span>地图搜索 · {{ activeKeyword || '河北工程大学' }}</span><button type="button" @click="showMap = false">返回校园实景</button></div>
        <div class="map-result">
          <img src="/campus/2024-2.jpg" alt="河北工程大学校门" />
          <div class="map-result-content"><MapPin :size="28" /><h3>河北工程大学 {{ activeKeyword }}</h3><p>邯郸市 · 太极路19号</p><a :href="mapUrl" target="_blank" rel="noopener noreferrer">打开地图，查看位置与路线 ↗</a></div>
        </div>
        <p class="map-fallback">将在高德地图中搜索校内地点，可继续查看步行、公交和驾车路线。</p>
      </template>
      <button v-else type="button" class="guide-photo" @click="openPhoto()" aria-label="放大查看河北工程大学校园实景">
        <img src="/campus/campus-evening-4k.jpg" alt="河北工程大学湖畔建筑群，3839像素高清实景" loading="lazy" decoding="async" width="3839" height="1594" />
        <span class="photo-label">河工程 · 校园实景 <Maximize2 :size="18" /></span>
      </button>
    </div>
    <dialog ref="photoDialog" class="campus-lightbox" @close="showPhoto = false">
      <button type="button" aria-label="关闭校园大图" @click="photoDialog?.close()"><X :size="26" /></button>
      <img v-if="showPhoto" src="/campus/campus-evening-4k.jpg" alt="河北工程大学校园实景大图" />
    </dialog>
  </section>
</template>

<style scoped>
.campus-guide{display:grid;grid-template-columns:1fr 1fr;gap:56px;padding:72px min(8%,110px);background:#fff;scroll-margin-top:90px;align-items:center}
.guide-eyebrow{font-size:11px;letter-spacing:3px;color:#a82634;font-weight:800;margin-bottom:12px}
h2{font-size:34px;font-weight:800;letter-spacing:2px;color:#173b58;margin:0 0 18px}
.guide-description{color:#63788b;line-height:1.9;font-size:15px}.guide-address{display:flex;gap:8px;align-items:center;font-size:12px;color:#63788b;margin:18px 0}
.guide-search{display:flex;gap:10px;align-items:center;background:#f4f7fa;border:1px solid #d4dfe8;border-radius:12px;padding:7px 7px 7px 14px;color:#63788b}
.guide-search input{min-width:0;width:100%;background:transparent;outline:none;font-size:13px}.guide-search button{white-space:nowrap;background:#173b58;color:white;padding:11px 17px;border-radius:8px;font-size:13px}.guide-search:focus-within{outline:2px solid #a82634;outline-offset:2px}
.guide-places{display:flex;flex-wrap:wrap;gap:8px;margin:14px 0 20px}.guide-places button{background:#edf2f6;border-radius:20px;padding:7px 12px;font-size:12px;color:#254b68}.guide-places button:hover{background:#d4dfe8}
.guide-links{display:flex;flex-wrap:wrap;gap:22px}.guide-links a{display:flex;align-items:center;gap:4px;color:#a82634;font-size:12px;font-weight:600}
.guide-visual{min-width:0}.guide-photo{position:relative;width:100%;height:340px;border-radius:18px;overflow:hidden;text-align:left}.guide-photo img{display:block;width:100%;height:100%;object-fit:cover;transition:transform .7s cubic-bezier(.2,.7,.2,1)}.guide-photo:hover img{transform:scale(1.035)}.photo-label{position:absolute;bottom:0;left:0;right:0;background:linear-gradient(transparent,#112a40dc);color:white;display:flex;justify-content:space-between;align-items:center;padding:40px 24px 24px;font-size:14px}
iframe{width:100%;height:330px;border:1px solid #d4dfe8;border-radius:12px;background:#edf2f6}.map-caption{display:flex;justify-content:space-between;gap:12px;font-size:12px;margin-bottom:10px;color:#173b58}.map-caption button,.map-fallback a{color:#a82634}.map-fallback{font-size:12px;color:#63788b;margin:10px 0 0}
.map-result{height:300px;position:relative;overflow:hidden;border-radius:18px;background:#173b58}.map-result>img{width:100%;height:100%;object-fit:cover;display:block}.map-result-content{position:absolute;inset:0;background:linear-gradient(90deg,#10283bed,#10283b70);color:white;display:flex;flex-direction:column;justify-content:center;align-items:flex-start;padding:28px;gap:12px}.map-result h3{font-size:20px;font-weight:700}.map-result p{font-size:12px;color:#d4dfe8}.map-result a{font-size:13px;border-radius:8px;background:#fff;color:#173b58;padding:12px 18px}
.campus-lightbox[open]{position:fixed;inset:0;z-index:1000;width:100vw;height:100vh;max-width:none;max-height:none;background:#10283beF;display:flex;align-items:center;justify-content:center;padding:60px 20px}.campus-lightbox img{max-width:95vw;max-height:85vh;object-fit:contain}.campus-lightbox button{position:absolute;right:24px;top:22px;color:white;padding:10px}
@media(max-width:900px){.campus-guide{grid-template-columns:1fr;gap:28px;padding:42px 24px}.guide-photo{height:280px}h2{font-size:28px}.guide-search button{padding:11px}.guide-address{font-size:11px}}
@media(prefers-reduced-motion:reduce){.guide-photo img{transition:none}.guide-photo:hover img{transform:none}}
</style>
