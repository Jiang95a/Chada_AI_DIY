import type { Doc } from '@/types/editor'
import { createBlankDoc } from '@/data/templates'
import { normalizeDoc } from '@/utils/doc'
import { apiRequest, readData } from './http'

export interface ApiProjectDto {
  id: string
  title: string
  activityType?: string
  type?: string
  status?: string
  coverTone?: string
  coverCode?: string
  coverTitle?: string
  submissionCount?: number
  draftDocument?: unknown
  document?: unknown
  draftRevision?: number
  publicSlug?: string
  publicUrl?: string
  publishedVersionId?: string
  createdAt?: string | number
  updatedAt?: string | number
}

export interface ProjectApiRecord {
  id: string
  title: string
  activityType: string
  status: string
  coverTone: string
  coverCode: string
  coverTitle: string
  submissionCount: number
  draftDocument: Doc
  draftRevision: number
  publicSlug: string
  publicUrl: string
}

function toTime(value: string | number | undefined): number {
  if (typeof value === 'number') return value
  if (!value) return Date.now()
  return new Date(value).getTime() || Date.now()
}

export function mapProject(dto: ApiProjectDto): ProjectApiRecord {
  const document = normalizeDoc(dto.draftDocument ?? dto.document) ?? {
    ...createBlankDoc(),
    id: dto.id,
    title: dto.title || '未命名活动',
    slug: dto.publicSlug || dto.id,
  }
  document.id = dto.id
  document.title = dto.title || document.title
  document.slug = dto.publicSlug || document.slug
  document.updatedAt = toTime(dto.updatedAt)
  if (dto.createdAt) document.createdAt = toTime(dto.createdAt)
  if (dto.publicUrl && dto.publicSlug) {
    document.published = { url: dto.publicUrl, slug: dto.publicSlug, at: document.updatedAt }
  }
  return {
    id: dto.id,
    title: dto.title,
    activityType: dto.activityType ?? dto.type ?? '心理活动',
    status: dto.status ?? 'draft',
    coverTone: dto.coverTone ?? 'photo',
    coverCode: dto.coverCode ?? 'ACTIVITY',
    coverTitle: dto.coverTitle ?? dto.title,
    submissionCount: Number(dto.submissionCount) || 0,
    draftDocument: document,
    draftRevision: Number(dto.draftRevision) || 0,
    publicSlug: dto.publicSlug ?? '',
    publicUrl: dto.publicUrl ?? '',
  }
}

export interface ProjectListParams {
  q?: string
  status?: string
  activityType?: string
  sort?: string
  page?: number
  pageSize?: number
}

export async function listProjects(params: ProjectListParams = {}): Promise<ProjectApiRecord[]> {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value))
  }
  const payload = await apiRequest<unknown>(`/projects?${search.toString()}`)
  const data = readData<unknown>(payload)
  const rows = Array.isArray(data)
    ? data
    : Array.isArray((data as { content?: unknown[] })?.content)
      ? (data as { content: unknown[] }).content
      : Array.isArray((data as { items?: unknown[] })?.items)
        ? (data as { items: unknown[] }).items
        : []
  return (rows as ApiProjectDto[]).map(mapProject)
}

export async function getProject(id: string): Promise<ProjectApiRecord> {
  return mapProject(readData<ApiProjectDto>(await apiRequest(`/projects/${id}`)))
}

export async function createProject(input: {
  title: string
  activityType: string
  templateId?: string
  initialDocument?: Doc
  coverTone?: string
  coverCode?: string
  coverTitle?: string
}): Promise<ProjectApiRecord> {
  const data = readData<ApiProjectDto | { project: ApiProjectDto }>(
    await apiRequest('/projects', {
      method: 'POST',
      body: JSON.stringify(input),
    })
  )
  return mapProject('project' in data ? data.project : data)
}

export async function updateProject(
  id: string,
  input: Partial<{
    title: string
    activityType: string
    status: string
    coverTone: string
    coverCode: string
    coverTitle: string
  }>
): Promise<ProjectApiRecord> {
  return mapProject(
    readData<ApiProjectDto>(
      await apiRequest(`/projects/${id}`, {
        method: 'PUT',
        body: JSON.stringify(input),
      })
    )
  )
}

export async function duplicateProject(id: string, title: string): Promise<ProjectApiRecord> {
  return mapProject(
    readData<ApiProjectDto>(
      await apiRequest(`/projects/${id}/duplicate`, {
        method: 'POST',
        body: JSON.stringify({ title }),
      })
    )
  )
}

export async function removeProject(id: string): Promise<void> {
  await apiRequest(`/projects/${id}`, { method: 'DELETE' })
}
