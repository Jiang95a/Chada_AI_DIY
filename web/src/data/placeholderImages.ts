/**
 * 本地占位图：用多色 SVG 图形生成可离线使用的封面。
 * 不使用渐变，避免缩略图失去层次；真实活动照片可以在编辑器里直接替换。
 */

function art(colors: [string, string, string], variant: 'soft' | 'film' | 'grid' = 'soft'): string {
  const [a, b, c] = colors
  const decorations =
    variant === 'film'
      ? `<g opacity=".38" stroke="#fff" stroke-width="3" stroke-linecap="round">
           <path d="M-20 306 258 28" /><path d="M42 356 340 58" /><path d="M134 398 416 116" />
         </g>`
      : variant === 'grid'
        ? `<g opacity=".24" stroke="#fff" stroke-width="2">
             <path d="M0 90h480M0 180h480M0 270h480M120 0v360M240 0v360M360 0v360" />
           </g>`
        : `<g opacity=".34" fill="#fff">
             <circle cx="392" cy="72" r="42" />
             <path d="M0 292c86-63 163-58 238-2 72 54 145 56 242 2v68H0Z" />
           </g>`

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 360" preserveAspectRatio="xMidYMid slice">
  <rect width="480" height="360" fill="${a}"/>
  <circle cx="92" cy="62" r="88" fill="${c}"/>
  <circle cx="408" cy="304" r="142" fill="${b}"/>
  <path d="M0 230c77-50 148-49 218-5 78 50 163 53 262 0v135H0Z" fill="${b}" opacity=".82"/>
  <path d="M0 270c96-43 177-36 249 6 73 43 149 44 231 8v76H0Z" fill="${a}" opacity=".5"/>
  ${decorations}
</svg>`

  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

export const PLACEHOLDER_IMAGES = {
  dusk: art(['#312e81', '#7c3aed', '#f472b6'], 'soft'),
  dawn: art(['#f59e0b', '#fb7185', '#fde68a'], 'film'),
  forest: art(['#065f46', '#10b981', '#a3e635'], 'grid'),
  ocean: art(['#0c4a6e', '#0284c7', '#67e8f9'], 'soft'),
  ink: art(['#111827', '#334155', '#94a3b8'], 'film'),
  clay: art(['#7c2d12', '#ea580c', '#fcd34d'], 'grid'),
} as const

export type PlaceholderKey = keyof typeof PLACEHOLDER_IMAGES

export function placeholder(key: PlaceholderKey): string {
  return PLACEHOLDER_IMAGES[key]
}
