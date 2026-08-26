<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import {
  createCommunityPost,
  publishCommunityPost,
  updateCommunityPost,
  uploadCommunityMedia,
  type CommunityMedia,
  type CommunityPost,
  type CommunityTopic,
  listCommunityTopics,
} from '../../api/community'
import { ApiRequestError } from '../../api/http'
import { listPetProfiles, type PetProfile } from '../../api/pet-profile'
import { useAuthStore } from '../../stores/auth'
import CommunityMediaGallery from './CommunityMediaGallery.vue'

const props = defineProps<{ modelValue: boolean; post?: CommunityPost }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [post: CommunityPost]
}>()

const auth = useAuthStore()
const topics = ref<CommunityTopic[]>([])
const profiles = ref<PetProfile[]>([])
const uploadedMedia = ref<CommunityMedia[]>([])
const saving = ref(false)
const loadingOptions = ref(false)
const form = reactive({
  title: '', content: '', topicId: '', petProfileId: '', region: '',
  latitude: undefined as number | undefined,
  longitude: undefined as number | undefined,
  version: 1,
})

/** 每次打开都从传入帖子重建表单，关闭后不会残留上一条草稿。 */
async function prepare(): Promise<void> {
  const post = props.post
  Object.assign(form, post ? {
    title: post.title,
    content: post.content,
    topicId: post.topicId ?? '',
    petProfileId: post.petProfileId ?? '',
    region: post.region ?? '',
    latitude: post.latitude ?? undefined,
    longitude: post.longitude ?? undefined,
    version: post.version,
  } : {
    title: '', content: '', topicId: '', petProfileId: '',
    region: auth.user?.region ?? '', latitude: undefined, longitude: undefined, version: 1,
  })
  uploadedMedia.value = post ? [...post.media] : []
  if (topics.value.length && profiles.value.length) return
  loadingOptions.value = true
  const [topicResult, profileResult] = await Promise.allSettled([listCommunityTopics(), listPetProfiles()])
  if (topicResult.status === 'fulfilled') topics.value = topicResult.value
  if (profileResult.status === 'fulfilled') profiles.value = profileResult.value
  loadingOptions.value = false
}

watch(() => props.modelValue, (open) => { if (open) void prepare() })

async function upload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    uploadedMedia.value.push(await uploadCommunityMedia(file))
    ElMessage.success('媒体上传完成，可以直接预览')
  } catch (cause) {
    ElMessage.error(readable(cause))
  } finally {
    input.value = ''
  }
}

function removeMedia(mediaId: string): void {
  uploadedMedia.value = uploadedMedia.value.filter((item) => item.id !== mediaId)
}

async function locate(): Promise<void> {
  if (!navigator.geolocation) throw new Error('当前浏览器不支持定位')
  const position = await new Promise<GeolocationPosition>((resolve, reject) =>
    navigator.geolocation.getCurrentPosition(resolve, reject, { enableHighAccuracy: false, timeout: 8000 }),
  )
  form.latitude = Number(position.coords.latitude.toFixed(7))
  form.longitude = Number(position.coords.longitude.toFixed(7))
  ElMessage.success('已记录当前位置')
}

async function save(publish: boolean): Promise<void> {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('请填写标题和正文')
    return
  }
  saving.value = true
  try {
    const input = {
      ...form,
      topicId: form.topicId || undefined,
      petProfileId: form.petProfileId || undefined,
      region: form.region || undefined,
      mediaIds: uploadedMedia.value.map((media) => media.id),
    }
    let saved = props.post
      ? await updateCommunityPost(props.post.id, input)
      : await createCommunityPost(input)
    if (publish && saved.status !== 'PUBLISHED') saved = await publishCommunityPost(saved.id)
    emit('saved', saved)
    emit('update:modelValue', false)
    ElMessage.success(publish ? '帖子已发布' : props.post ? '修改已保存' : '草稿已保存')
  } catch (cause) {
    ElMessage.error(readable(cause))
  } finally {
    saving.value = false
  }
}

function readable(cause: unknown): string {
  if (cause instanceof ApiRequestError) return cause.message
  if (cause instanceof GeolocationPositionError) return '无法取得定位，请允许浏览器定位权限'
  return cause instanceof Error ? cause.message : '请求失败，请稍后重试'
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="post ? '编辑动态' : '发布新动态'"
    width="min(760px, 94vw)"
    class="post-composer-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p v-if="loadingOptions" class="muted" role="status">正在加载话题和宠物档案…</p>
    <el-form v-else label-position="top">
      <div class="form-grid">
        <el-form-item class="span-2" label="标题"><el-input v-model="form.title" maxlength="160" show-word-limit placeholder="一句话说清这次分享" /></el-form-item>
        <el-form-item label="话题"><el-select v-model="form.topicId" clearable><el-option v-for="topic in topics" :key="topic.id" :label="topic.name" :value="topic.id" /></el-select></el-form-item>
        <el-form-item label="关联宠物"><el-select v-model="form.petProfileId" clearable><el-option v-for="profile in profiles" :key="profile.id" :label="profile.name" :value="profile.id" /></el-select></el-form-item>
        <el-form-item label="地区"><el-input v-model="form.region" maxlength="100" /></el-form-item>
        <el-form-item label="附近推荐位置"><button type="button" class="button button-secondary" @click="locate">{{ form.latitude == null ? '使用当前位置' : '已记录位置' }}</button></el-form-item>
        <el-form-item class="span-2" label="正文"><el-input v-model="form.content" type="textarea" :rows="7" maxlength="10000" show-word-limit placeholder="分享真实经历；健康建议请注明适用条件和风险。" /></el-form-item>
        <el-form-item class="span-2" label="图片或短视频">
          <input type="file" accept="image/jpeg,image/png,image/webp,video/mp4,video/webm" :disabled="uploadedMedia.length >= 6" @change="upload">
          <CommunityMediaGallery v-if="uploadedMedia.length" :media="uploadedMedia" :alt="form.title || '待发布动态'" />
          <div v-if="uploadedMedia.length" class="editor-media-actions"><button v-for="(media, index) in uploadedMedia" :key="media.id" type="button" @click="removeMedia(media.id)">移除第 {{ index + 1 }} 项</button></div>
        </el-form-item>
      </div>
    </el-form>
    <template #footer><button class="button button-secondary" @click="emit('update:modelValue', false)">取消</button><button class="button button-secondary" :disabled="saving" @click="save(false)">{{ post ? '保存修改' : '保存草稿' }}</button><button class="button button-primary" :disabled="saving" @click="save(true)">保存并发布</button></template>
  </el-dialog>
</template>
