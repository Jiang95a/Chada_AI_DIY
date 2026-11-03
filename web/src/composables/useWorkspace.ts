import { computed, ref } from 'vue'

export interface WorkspaceOption {
  id: string
  name: string
  type: string
  avatar: string
}

const WORKSPACES: WorkspaceOption[] = [
  { id: 'student-center', name: '心屿心理协会', type: '社团工作空间', avatar: '心' },
  { id: 'personal', name: 'Huiyi 的个人空间', type: '个人工作空间', avatar: 'H' },
  { id: 'campus-life', name: '心晴志愿队', type: '心理服务空间', avatar: '晴' },
]

const STORAGE_KEY = 'diy-campus:workspace:v1'
const selectedId = ref(localStorage.getItem(STORAGE_KEY) || WORKSPACES[0]!.id)
const selected = computed(() => WORKSPACES.find((workspace) => workspace.id === selectedId.value) ?? WORKSPACES[0]!)

export function useWorkspace() {
  function selectWorkspace(id: string): void {
    if (!WORKSPACES.some((workspace) => workspace.id === id)) return
    selectedId.value = id
    localStorage.setItem(STORAGE_KEY, id)
  }

  return { workspaces: WORKSPACES, selected, selectedId, selectWorkspace }
}
