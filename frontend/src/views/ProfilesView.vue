<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { ApiRequestError } from '../api/http'
import {
  createPetProfile,
  deletePetProfile,
  listPetProfiles,
  updatePetProfile,
  type PetProfile,
} from '../api/pet-profile'
import PageState from '../components/PageState.vue'

const profiles = ref<PetProfile[]>([])
const loading = ref(true)
const error = ref('')
const dialogOpen = ref(false)
const editingId = ref<string>()
const saving = ref(false)
const form = reactive<{ name: string; petType: 'CAT' | 'DOG' | 'OTHER'; breed: string; ageMonths?: number; weightKg?: number; notes: string }>({
  name: '', petType: 'CAT', breed: '', ageMonths: undefined, weightKg: undefined, notes: '',
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try { profiles.value = await listPetProfiles() }
  catch (cause) { error.value = readable(cause) }
  finally { loading.value = false }
}

function openCreate(): void {
  editingId.value = undefined
  Object.assign(form, { name: '', petType: 'CAT', breed: '', ageMonths: undefined, weightKg: undefined, notes: '' })
  dialogOpen.value = true
}

function openEdit(profile: PetProfile): void {
  editingId.value = profile.id
  Object.assign(form, {
    name: profile.name, petType: profile.petType, breed: profile.breed ?? '',
    ageMonths: profile.ageMonths ?? undefined, weightKg: profile.weightKg ?? undefined, notes: profile.notes ?? '',
  })
  dialogOpen.value = true
}

async function save(): Promise<void> {
  if (!form.name.trim()) return
  saving.value = true
  try {
    const request = { ...form, breed: form.breed || undefined, notes: form.notes || undefined }
    if (editingId.value) await updatePetProfile(editingId.value, request)
    else await createPetProfile(request)
    dialogOpen.value = false
    ElMessage.success(editingId.value ? '档案已更新' : '档案已创建')
    await load()
  } catch (cause) { ElMessage.error(readable(cause)) }
  finally { saving.value = false }
}

async function remove(profile: PetProfile): Promise<void> {
  await ElMessageBox.confirm(`确认删除“${profile.name}”的档案吗？`, '删除档案', { type: 'warning' })
  await deletePetProfile(profile.id)
  ElMessage.success('档案已删除')
  await load()
}

function readable(cause: unknown): string {
  return cause instanceof ApiRequestError ? cause.message : '宠物档案加载失败'
}

onMounted(load)
</script>

<template>
  <section class="profiles-page">
    <header class="page-heading"><div><p class="eyebrow">PET PROFILES</p><h1>我的宠物伙伴</h1><p>档案会帮助智能问答理解宠物类型、年龄和体重。</p></div><button class="button button-primary" @click="openCreate">＋ 新建档案</button></header>
    <div class="profiles-scroll-region" aria-label="宠物档案功能区">
      <PageState v-if="loading" type="loading" message="正在加载宠物档案…" />
      <PageState v-else-if="error" type="error" :message="error" @retry="load" />
      <PageState v-else-if="profiles.length === 0" type="empty" message="还没有宠物档案，先记录第一位家庭成员吧。" />
      <div v-else class="profile-gallery">
        <article v-for="profile in profiles" :key="profile.id" class="pet-profile-card">
          <div class="pet-profile-cover"><span>{{ profile.petType === 'CAT' ? '🐈' : profile.petType === 'DOG' ? '🐕' : '🐾' }}</span></div>
          <div class="pet-profile-body"><span class="topic-pill">{{ profile.petType }}</span><h2>{{ profile.name }}</h2><p>{{ profile.breed || '未填写品种' }}</p><div class="pet-facts"><span>{{ profile.ageMonths ?? '—' }} 月龄</span><span>{{ profile.weightKg ?? '—' }} kg</span></div><small>{{ profile.notes || '还没有备注' }}</small><div class="card-actions"><RouterLink class="button button-secondary" :to="`/app/chat?profile=${profile.id}`">用于问答</RouterLink><button class="button button-ghost" @click="openEdit(profile)">编辑</button><button class="text-danger" @click="remove(profile)">删除</button></div></div>
        </article>
      </div>
    </div>

    <el-dialog v-model="dialogOpen" :title="editingId ? '编辑宠物档案' : '新建宠物档案'" width="min(560px, 92vw)">
      <el-form label-position="top">
        <div class="form-grid"><el-form-item label="宠物名称"><el-input v-model="form.name" maxlength="80" /></el-form-item><el-form-item label="宠物类型"><el-select v-model="form.petType"><el-option label="猫" value="CAT" /><el-option label="狗" value="DOG" /><el-option label="其他" value="OTHER" /></el-select></el-form-item><el-form-item label="品种"><el-input v-model="form.breed" maxlength="100" /></el-form-item><el-form-item label="月龄"><el-input-number v-model="form.ageMonths" :min="0" :max="600" /></el-form-item><el-form-item label="体重（kg）"><el-input-number v-model="form.weightKg" :min="0.01" :max="9999" :precision="2" /></el-form-item><el-form-item class="span-2" label="备注"><el-input v-model="form.notes" type="textarea" :rows="3" maxlength="1000" /></el-form-item></div>
      </el-form>
      <template #footer><button class="button button-secondary" @click="dialogOpen = false">取消</button><button class="button button-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button></template>
    </el-dialog>
  </section>
</template>
