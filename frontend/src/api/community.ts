import { apiRequest } from './http'

export interface CommunityTopic { id: string; name: string; description: string | null }
export interface CommunityMedia {
  id: string
  mediaType: 'IMAGE' | 'VIDEO'
  contentType: string
  originalFilename: string
  sizeBytes: number
  status: string
  processingStatus: string
  confirmedAt: string | null
}
export interface CommunityPost {
  id: string
  authorId: string
  authorUsername: string
  authorDisplayName: string
  petProfileId: string | null
  petName: string | null
  topicId: string | null
  topicName: string | null
  title: string
  content: string
  region: string | null
  latitude: number | null
  longitude: number | null
  status: 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'HIDDEN'
  viewCount: number
  likeCount: number
  commentCount: number
  favoriteCount: number
  version: number
  publishedAt: string | null
  createdAt: string
  updatedAt: string
  media: CommunityMedia[]
  viewerLiked: boolean
  viewerFavorited: boolean
  viewerFollowsAuthor: boolean
}
export interface PostPage { items: CommunityPost[]; page: number; size: number; total: number }
export type CommunityFeed = 'LATEST' | 'HOT' | 'FOLLOWING' | 'NEARBY'
export interface CommunityPostInput {
  title: string
  content: string
  petProfileId?: string
  topicId?: string
  region?: string
  latitude?: number
  longitude?: number
  mediaIds: string[]
  version?: number
}

export interface CommunityComment {
  id: string
  postId: string
  authorId: string
  authorUsername: string
  authorDisplayName: string
  parentId: string | null
  rootId: string | null
  depth: 0 | 1
  content: string
  likeCount: number
  viewerCanDelete: boolean
  createdAt: string
  updatedAt: string
}

export interface CommunityReport {
  id: string
  reporterId: string
  reporterUsername: string
  targetType: 'POST' | 'COMMENT' | 'USER'
  targetId: string
  reasonType: string
  description: string | null
  status: 'PENDING' | 'RESOLVED' | 'REJECTED'
  resolution: string | null
  moderatorId: string | null
  moderatorUsername: string | null
  moderatorNote: string | null
  version: number
  createdAt: string
  resolvedAt: string | null
}

interface CommunityCommentPage { items: CommunityComment[]; page: number; size: number; total: number }
export interface CommunityReportPage { items: CommunityReport[]; page: number; size: number; total: number }
export interface CommunityReaction { active: boolean; count: number }
export interface CommunityCheckIn { date: string; checkedIn: boolean; daysThisMonth: number; currentStreak: number }
export interface CommunityAnalytics { date: string; approximateFeedUv: number; pendingReports: number }
export interface PublicPetSummary { id: string; name: string; petType: string; breed: string | null; ageMonths: number | null }
export interface PublicUserProfile {
  id: string
  username: string
  displayName: string
  avatarUrl: string | null
  bio: string | null
  region: string | null
  joinedAt: string
  postCount: number
  followerCount: number
  followingCount: number
  viewerFollowing: boolean
  ownProfile: boolean
  pets: PublicPetSummary[]
}
export interface PublicUserSummary {
  id: string
  username: string
  displayName: string
  avatarUrl: string | null
  bio: string | null
  region: string | null
  viewerFollowing: boolean
}
export interface PublicUserPage { items: PublicUserSummary[]; page: number; size: number; total: number }
interface MediaUpload {
  mediaId: string
  objectKey: string
  uploadUrl: string
  httpMethod: 'PUT'
  expiresAt: string
}

export const listCommunityTopics = () => apiRequest<CommunityTopic[]>('/api/v1/community/topics')
export function listCommunityPosts(params: {
  feed?: CommunityFeed
  topicId?: string
  authorId?: string
  latitude?: number
  longitude?: number
  radiusKm?: number
  page?: number
  size?: number
} = {}): Promise<PostPage> {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })
  return apiRequest(`/api/v1/community/posts?${query.toString()}`)
}
export const listMyCommunityPosts = () => apiRequest<PostPage>('/api/v1/community/posts/mine')
export const getCommunityPost = (postId: string) => apiRequest<CommunityPost>(`/api/v1/community/posts/${postId}`)

export function createCommunityPost(input: CommunityPostInput): Promise<CommunityPost> {
  return apiRequest('/api/v1/community/posts', { method: 'POST', body: JSON.stringify(input) })
}

export function updateCommunityPost(postId: string, input: CommunityPostInput): Promise<CommunityPost> {
  return apiRequest(`/api/v1/community/posts/${postId}`, { method: 'PUT', body: JSON.stringify(input) })
}

export const publishCommunityPost = (postId: string) => apiRequest<CommunityPost>(
  `/api/v1/community/posts/${postId}/publish`, { method: 'POST' },
)
export const deleteCommunityPost = (postId: string) => apiRequest<void>(
  `/api/v1/community/posts/${postId}`, { method: 'DELETE' },
)

export const listCommunityComments = (postId: string) => apiRequest<CommunityCommentPage>(
  `/api/v1/community/posts/${postId}/comments?size=100`,
)
export const createCommunityComment = (postId: string, content: string, parentId?: string) =>
  apiRequest<CommunityComment>(`/api/v1/community/posts/${postId}/comments`, {
    method: 'POST', body: JSON.stringify({ content, parentId }),
  })
export const deleteCommunityComment = (commentId: string) => apiRequest<void>(
  `/api/v1/community/comments/${commentId}`, { method: 'DELETE' },
)

export const setPostLike = (postId: string, active: boolean) => apiRequest<CommunityReaction>(
  `/api/v1/community/posts/${postId}/like`, { method: active ? 'PUT' : 'DELETE' },
)
export const setPostFavorite = (postId: string, active: boolean) => apiRequest<CommunityReaction>(
  `/api/v1/community/posts/${postId}/favorite`, { method: active ? 'PUT' : 'DELETE' },
)
export const setUserFollow = (userId: string, active: boolean) => apiRequest<{ following: boolean; followerCount: number }>(
  `/api/v1/community/users/${userId}/follow`, { method: active ? 'PUT' : 'DELETE' },
)

export const getPublicUserProfile = (userId: string) => apiRequest<PublicUserProfile>(
  `/api/v1/community/users/${userId}/profile`,
)
export const listUserFollowers = (userId: string, page = 0, size = 20) => apiRequest<PublicUserPage>(
  `/api/v1/community/users/${userId}/followers?page=${page}&size=${size}`,
)
export const listUserFollowing = (userId: string, page = 0, size = 20) => apiRequest<PublicUserPage>(
  `/api/v1/community/users/${userId}/following?page=${page}&size=${size}`,
)
export const listMyLikedPosts = (page = 0, size = 20) => apiRequest<PostPage>(
  `/api/v1/community/users/me/liked-posts?page=${page}&size=${size}`,
)
export const listMyFavoritePosts = (page = 0, size = 20) => apiRequest<PostPage>(
  `/api/v1/community/users/me/favorite-posts?page=${page}&size=${size}`,
)

export const createCommunityReport = (input: {
  targetType: 'POST' | 'COMMENT' | 'USER'
  targetId: string
  reasonType: string
  description?: string
}) => apiRequest<CommunityReport>('/api/v1/community/reports', {
  method: 'POST', body: JSON.stringify(input),
})

export const getCheckInStatus = () => apiRequest<CommunityCheckIn>('/api/v1/community/check-ins/today')
export const checkInToday = () => apiRequest<CommunityCheckIn>(
  '/api/v1/community/check-ins/today', { method: 'PUT' },
)

export function listModerationReports(status = 'PENDING'): Promise<CommunityReportPage> {
  return apiRequest(`/api/v1/moderation/community/reports?status=${encodeURIComponent(status)}`)
}
export const moderateCommunityReport = (
  reportId: string,
  input: { action: 'NO_ACTION' | 'HIDE_CONTENT' | 'WARN_USER'; note?: string; version: number },
) => apiRequest<CommunityReport>(`/api/v1/moderation/community/reports/${reportId}`, {
  method: 'PUT', body: JSON.stringify(input),
})
export const getCommunityAnalytics = () => apiRequest<CommunityAnalytics>(
  '/api/v1/moderation/community/analytics/today',
)

/** 浏览器先取得 PUT 地址直传 MinIO，再调用确认接口。 */
export function createMediaUpload(file: File): Promise<MediaUpload> {
  return apiRequest('/api/v1/community/media/upload-url', {
    method: 'POST',
    body: JSON.stringify({ fileName: file.name, contentType: file.type, sizeBytes: file.size }),
  })
}

export const confirmMediaUpload = (mediaId: string) => apiRequest<CommunityMedia>(
  `/api/v1/community/media/${mediaId}/confirm`, { method: 'POST' },
)

export async function uploadCommunityMedia(file: File): Promise<CommunityMedia> {
  const ticket = await createMediaUpload(file)
  const response = await fetch(ticket.uploadUrl, {
    method: ticket.httpMethod,
    headers: { 'Content-Type': file.type },
    body: file,
  })
  if (!response.ok) throw new Error(`上传 MinIO 失败（HTTP ${response.status}）`)
  return confirmMediaUpload(ticket.mediaId)
}

/** 卡片图片使用短期地址；对象 Key 永远不进入浏览器响应。 */
export const getCommunityMediaUrl = (mediaId: string) => apiRequest<{ url: string; expiresAt?: string }>(
  `/api/v1/community/media/${mediaId}/download-url`,
)
