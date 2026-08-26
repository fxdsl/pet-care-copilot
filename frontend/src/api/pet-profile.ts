import { apiRequest } from './http'

/** 创建宠物档案的页面请求。 */
export interface CreatePetProfileRequest {
  name: string
  petType: 'CAT' | 'DOG' | 'OTHER'
  breed?: string
  ageMonths?: number
  weightKg?: number
  notes?: string
}

/** 可供问答选择的宠物档案；后端未填写的可选字段会返回 null。 */
export interface PetProfile {
  id: string
  userId: string
  name: string
  petType: 'CAT' | 'DOG' | 'OTHER'
  breed: string | null
  ageMonths: number | null
  weightKg: number | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** 更新档案使用与创建相同的完整字段，避免部分更新歧义。 */
export function updatePetProfile(
  profileId: string,
  request: CreatePetProfileRequest,
): Promise<PetProfile> {
  return apiRequest<PetProfile>(
    `/api/v1/pet-profiles/${encodeURIComponent(profileId)}`,
    { method: 'PUT', body: JSON.stringify(request) },
  )
}

/** 删除当前用户自己的档案。 */
export function deletePetProfile(profileId: string): Promise<void> {
  return apiRequest<void>(
    `/api/v1/pet-profiles/${encodeURIComponent(profileId)}`,
    { method: 'DELETE' },
  )
}

/** 创建档案并返回数据库记录。 */
export function createPetProfile(
  request: CreatePetProfileRequest,
): Promise<PetProfile> {
  return apiRequest<PetProfile>('/api/v1/pet-profiles', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

/** 查询当前可选的宠物档案。 */
export function listPetProfiles(limit = 50): Promise<PetProfile[]> {
  return apiRequest<PetProfile[]>(`/api/v1/pet-profiles?limit=${limit}`)
}
