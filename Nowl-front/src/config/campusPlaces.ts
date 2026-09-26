// Image coordinates on the school's public 1800 × 732 sand table.
// Markers identify building areas, not GPS coordinates or exact entrances.
export const campusMap = {
  image: '/campus/campus-sandtable.jpg',
  width: 1800,
  height: 732,
  source: 'https://www.720yun.com/t/2avktm1qs2m',
} as const

export interface CampusPlace {
  id: string
  name: string
  aliases: string[]
  x: number
  y: number
  category: string
  detail: string
}

export const campusPlaces: CampusPlace[] = [
  {
    id: 'library',
    name: '图书馆',
    aliases: ['图书', '1号楼', '一号楼'],
    x: 901,
    y: 401,
    category: '学习',
    detail: '校园中轴线上的图书馆。见面前请在聊天中约定具体入口。',
  },
  {
    id: 'library-square',
    name: '图书馆南广场',
    aliases: ['主广场', '南广场'],
    x: 901,
    y: 489,
    category: '公共空间',
    detail: '图书馆南侧公共广场，可与对方约定清楚的面交位置。',
  },
  {
    id: 'teaching-1',
    name: '第一公共教学楼',
    aliases: ['一教', '教一', '第一教学楼', '2号楼', '二号楼'],
    x: 725,
    y: 333,
    category: '学习',
    detail: '2号楼，位于图书馆西北侧；具体教室请以楼内标识为准。',
  },
  {
    id: 'teaching-2',
    name: '第二公共教学楼',
    aliases: ['二教', '教二', '第二教学楼', '3号楼', '三号楼'],
    x: 1057,
    y: 333,
    category: '学习',
    detail: '3号楼，位于图书馆东北侧；具体教室请以楼内标识为准。',
  },
  {
    id: 'lake',
    name: '精工湖',
    aliases: ['湖', '湖边'],
    x: 909,
    y: 249,
    category: '校园景观',
    detail: '校园中部的精工湖。湖区较大，见面时请进一步约定方位。',
  },
  {
    id: 'south-gate',
    name: '南门（正门）',
    aliases: ['南门', '正门', '校门', '太极路'],
    x: 899,
    y: 620,
    category: '校门',
    detail: '太极路一侧的学校正门，通向校园中轴线。',
  },
  {
    id: 'stadium',
    name: '体育场',
    aliases: ['运动场', '操场', '足球场', '田径场'],
    x: 230,
    y: 481,
    category: '运动',
    detail: '校园西南侧的体育场，图中标记为场馆区域。',
  },
  {
    id: 'conference',
    name: '会议服务中心',
    aliases: ['5号楼', '五号楼', '会议中心'],
    x: 743,
    y: 470,
    category: '公共服务',
    detail: '5号楼，位于图书馆南广场西侧。',
  },
  {
    id: 'research',
    name: '科研楼',
    aliases: ['4号楼', '四号楼', '计算机部', '现代教育技术中心', '分析测试中心'],
    x: 1069,
    y: 472,
    category: '教学科研',
    detail: '4号楼，位于图书馆南广场东侧。',
  },
]

export function searchCampusPlaces(query: string): CampusPlace[] {
  const value = query.trim().replace(/\s+/g, '').toLowerCase()
  if (!value) return campusPlaces
  const exact = campusPlaces.filter((place) =>
    [place.name, ...place.aliases].some((name) => name.toLowerCase() === value),
  )
  return [
    ...exact,
    ...campusPlaces.filter(
      (place) =>
        !exact.includes(place) &&
        [place.name, ...place.aliases, place.category].some((name) =>
          name.toLowerCase().includes(value),
        ),
    ),
  ]
}
