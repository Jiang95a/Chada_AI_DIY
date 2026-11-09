import { computed, ref } from 'vue'

export interface WorkspaceOption {
  id: string
  name: string
  type: string
  avatar: string
}

const WORKSPACES: WorkspaceOption[] = [
  { id: 'xinyu-club', name: '心屿心理协会', type: '心理社团 · 全校活动', avatar: '心' },
]

const selectedId = ref(WORKSPACES[0]!.id)
const selected = computed(() => WORKSPACES.find((workspace) => workspace.id === selectedId.value) ?? WORKSPACES[0]!)

export function useWorkspace() {
  return { workspaces: WORKSPACES, selected, selectedId }
}
