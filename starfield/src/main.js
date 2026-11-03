import * as THREE from 'three'
import cloudTextureUrl from './assets/cloud.png'

// ============================================================
// State
// ============================================================
let cards = []
let galaxies = []       // [{ name, cards, nebula, planets: [...] }]
let galaxyIdx = 0
let allPlanets = []     // flat list of all planet objects across galaxies
let currentCardIdx = 0  // index into cards[]

let scene, camera, renderer
let bgStars, nebulaGroup

const BG_STAR_COUNT = 900
const MAX_VISIBLE_PLANETS = 8

// Planet types — only Saturn has rings
const PLANET_TYPES = ['earth', 'saturn', 'moon', 'star', 'mars', 'jupiter', 'neptune', 'mercury', 'venus', 'uranus']
const PLANET_NAMES = {
  earth: '地球', saturn: '土星', moon: '月球',
  star: '恒星', mars: '火星', jupiter: '木星', neptune: '海王星',
  mercury: '水星', venus: '金星', uranus: '天王星'
}
const PLANET_RING = {
  earth: false, saturn: true, moon: false,
  star: false, mars: false, jupiter: false, neptune: false,
  mercury: false, venus: false, uranus: false
}
// Text colors per planet — bright hues visible on both planet and dark space
// With text outline, all colors are readable on any background
const PLANET_TEXT_COLORS = {
  earth:    { question: 'rgba(255,245,215,0.96)', answer: 'rgba(255,235,200,0.96)' },
  saturn:   { question: 'rgba(100,190,250,0.95)', answer: 'rgba(130,205,255,0.95)' },
  moon:     { question: 'rgba(120,155,230,0.96)', answer: 'rgba(150,175,240,0.96)' },
  star:     { question: 'rgba(140,95,220,0.95)',  answer: 'rgba(165,120,235,0.95)' },
  mars:     { question: 'rgba(80,210,200,0.95)',  answer: 'rgba(110,225,215,0.95)' },
  jupiter:  { question: 'rgba(60,165,210,0.95)',  answer: 'rgba(90,180,225,0.95)' },
  neptune:  { question: 'rgba(255,230,195,0.96)', answer: 'rgba(255,220,180,0.96)' },
  mercury:  { question: 'rgba(150,190,255,0.95)', answer: 'rgba(175,210,255,0.95)' },
  venus:    { question: 'rgba(90,200,220,0.95)',  answer: 'rgba(120,215,235,0.95)' },
  uranus:   { question: 'rgba(255,225,170,0.95)', answer: 'rgba(255,235,190,0.95)' },
}

// Read mode — planets hidden, auto-advance on card completion
let readMode = false
let readModePaused = false
let readModeTimer = 0
let readModeDelay = 0

// Cloud mode — fly-through 3D cloud field (ported from the classic three.js clouds demo)
let cloudMode = false
let cloudCanvas = null
let cloudAnimId = null
let cloudRenderer = null
let cloudScene = null
let cloudCamera = null
let cloudStartTime = 0

// 3D mode — Shadertoy "Another day, another glowmarcher" (t3GfDd, CC0)
let shaderMode = false
let shaderCanvas = null
let shaderGl = null
let shaderAnimId = null
let shaderUniforms = null
const SHADER_SCALE = 0.7
// 卡片翻页与 3D 画面共用一条时间轴：1 个「环」= 1 张卡片
// 档位 1–8：1× 最慢（每张 4 秒），8× 最快（每张 0.5 秒）
let shaderLevel = 1
let shaderAccum = 0
let shaderLastTs = 0
let shaderRingIdx = 0
const RING_SECONDS_SLOWEST = 4
const RING_SECONDS_FASTEST = 0.5
const RING_PER_CARD = 1

// Planet staging — maintain natural sweep coverage
let stagedBackup = null        // next planet queued off-screen
let entranceGap = 0            // frames until next planet may enter (0 = none)
let entranceGapTarget = 0      // total gap duration for display

// Typewriter — two-phase: front → pause → back
let paused = false         // HUD 播放/暂停：冻结卡片推进与动画时间轴
let typingPhase = 'idle'   // idle | front | pause | back | done
let revealedChars = 0
let frontCharCount = 0
let backCharCount = 0
let revealTimer = 0
let revealSpeed = 55
let pauseTimer = 0
const THINK_PAUSE = 90     // ~1.5s at 60fps

// Pre-computed text layout
let textLayout = []
let TEXT_FADE = 1

// Canvas
let textCanvas, textCtx
const FONT_SIZE = 16
const LINE_HEIGHT = FONT_SIZE * 1.4
const TEXT_PAD_X = 40

// ============================================================
// Bridge
// ============================================================
function getBridge() { return typeof window.MemoryCards !== 'undefined' ? window.MemoryCards : null }

function loadCards() {
  return new Promise((resolve) => {
    const bridge = getBridge()
    if (bridge) {
      try {
        const raw = bridge.getCards()
        cards = JSON.parse(raw)
        resolve()
      } catch (e) {
        window.loadCardsFromAndroid = (data) => { cards = data; resolve() }
        setTimeout(() => { if (cards.length === 0) { useMockCards(); resolve() } }, 3000)
      }
    } else {
      useMockCards()
      resolve()
    }
  })
}

function useMockCards() {
  cards = [
    { id: 1, front: '什么是 JVM？', back: 'Java Virtual Machine，Java 虚拟机', box: 1, categoryPath: 'Java' },
    { id: 2, front: 'HTTPS 端口号是多少？', back: '443', box: 5, categoryPath: '网络' },
    { id: 3, front: 'GC 全称是什么？', back: 'Garbage Collection，垃圾回收', box: 2, categoryPath: 'Java/JVM' },
    { id: 4, front: 'TCP 三次握手的过程？', back: 'SYN → SYN-ACK → ACK', box: 3, categoryPath: '网络' },
    { id: 5, front: 'HashMap 的线程安全替代方案？', back: 'ConcurrentHashMap 或 Collections.synchronizedMap', box: 1, categoryPath: 'Java/集合' },
    { id: 6, front: 'O(n) 和 O(1) 的区别？', back: '线性时间复杂度 vs 常数时间复杂度', box: 2, categoryPath: '算法' },
  ]
}

// ============================================================
// Planet textures (unchanged)
// ============================================================
function generateEarthTexture(w, h) {
  const c = document.createElement('canvas'); c.width = w; c.height = h; const ctx = c.getContext('2d')
  const ocean = ctx.createRadialGradient(w * 0.35, h * 0.4, 40, w / 2, h / 2, w * 0.55)
  ocean.addColorStop(0, '#1a6bcc'); ocean.addColorStop(0.3, '#2266cc'); ocean.addColorStop(0.7, '#1155aa'); ocean.addColorStop(1, '#0a3388')
  ctx.fillStyle = ocean; ctx.fillRect(0, 0, w, h)
  ctx.fillStyle = '#3a8c3a'
  drawContinent(ctx, w * 0.25, h * 0.35, w * 0.22, h * 0.18); drawContinent(ctx, w * 0.55, h * 0.3, w * 0.18, h * 0.20)
  drawContinent(ctx, w * 0.6, h * 0.55, w * 0.15, h * 0.16); drawContinent(ctx, w * 0.2, h * 0.6, w * 0.12, h * 0.10)
  ctx.fillStyle = '#55aa55'
  drawContinent(ctx, w * 0.28, h * 0.30, w * 0.14, h * 0.10); drawContinent(ctx, w * 0.58, h * 0.35, w * 0.10, h * 0.08)
  ctx.fillStyle = 'rgba(255,255,255,0.25)'
  for (let i = 0; i < 40; i++) { const cx = Math.random() * w, cy = Math.random() * h; ctx.beginPath(); ctx.arc(cx, cy, 8 + Math.random() * 25, 0, Math.PI * 2); ctx.fill() }
  return c
}

function generateSaturnTexture(w, h) {
  const c = document.createElement('canvas'); c.width = w; c.height = h; const ctx = c.getContext('2d')
  const base = ctx.createLinearGradient(0, 0, 0, h)
  base.addColorStop(0, '#f0d8a0'); base.addColorStop(0.2, '#e8c878'); base.addColorStop(0.4, '#d4b060')
  base.addColorStop(0.6, '#e8c878'); base.addColorStop(0.8, '#d4b060'); base.addColorStop(1, '#f0d8a0')
  ctx.fillStyle = base; ctx.fillRect(0, 0, w, h)
  for (let y = 0; y < h; y++) {
    const v = Math.sin(y * 0.06) * 0.06 + Math.sin(y * 0.12 + 1.5) * 0.04 + Math.sin(y * 0.03 + 3) * 0.05
    ctx.fillStyle = `rgba(${220 + v * 35},${170 + v * 30},${90 + v * 25},0.25)`; ctx.fillRect(0, y, w, 1)
  }
  return c
}

function generateMoonTexture(w, h) {
  const c = document.createElement('canvas'); c.width = w; c.height = h; const ctx = c.getContext('2d')
  const base = ctx.createRadialGradient(w * 0.4, h * 0.45, 30, w / 2, h / 2, w * 0.52)
  base.addColorStop(0, '#c8c8c8'); base.addColorStop(0.5, '#b0b0b0'); base.addColorStop(0.8, '#909090'); base.addColorStop(1, '#707070')
  ctx.fillStyle = base; ctx.fillRect(0, 0, w, h)
  for (let i = 0; i < 50; i++) {
    const cx = Math.random() * w * 0.85 + w * 0.075, cy = Math.random() * h * 0.85 + h * 0.075, r = 4 + Math.random() * 20
    ctx.fillStyle = 'rgba(100,100,100,0.5)'; ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.fill()
    ctx.strokeStyle = 'rgba(180,180,180,0.4)'; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(cx, cy, r * 0.9, 0, Math.PI * 2); ctx.stroke()
  }
  for (let i = 0; i < 8; i++) {
    ctx.fillStyle = 'rgba(80,80,80,0.2)'; ctx.beginPath()
    ctx.ellipse(w * 0.2 + Math.random() * w * 0.6, h * 0.2 + Math.random() * h * 0.6, w * 0.08 + Math.random() * w * 0.12, h * 0.06 + Math.random() * h * 0.08, Math.random() * Math.PI, 0, Math.PI * 2)
    ctx.fill()
  }
  return c
}

// ---- New models ----
function generateStarTexture(w, h) {
  const c = document.createElement('canvas'); c.width = w; c.height = h; const ctx = c.getContext('2d')
  const base = ctx.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w * 0.55)
  base.addColorStop(0, '#fffde0'); base.addColorStop(0.15, '#ffe040'); base.addColorStop(0.35, '#ffa020')
  base.addColorStop(0.6, '#ee6600'); base.addColorStop(0.85, '#aa3300'); base.addColorStop(1, '#661100')
  ctx.fillStyle = base; ctx.fillRect(0, 0, w, h)
  for (let i = 0; i < 20; i++) {
    ctx.fillStyle = `rgba(200,120,20,${0.12 + Math.random() * 0.25})`
    ctx.beginPath(); ctx.arc(w * 0.05 + Math.random() * w * 0.9, h * 0.05 + Math.random() * h * 0.9, 8 + Math.random() * 45, 0, Math.PI * 2); ctx.fill()
  }
  for (let i = 0; i < 60; i++) {
    ctx.fillStyle = `rgba(255,240,180,${0.05 + Math.random() * 0.10})`
    ctx.beginPath(); ctx.arc(w * Math.random(), h * Math.random(), 3 + Math.random() * 12, 0, Math.PI * 2); ctx.fill()
  }
  return c
}

function generateMarsTexture(w, h) {
  const c = document.createElement('canvas'); c.width = w; c.height = h; const ctx = c.getContext('2d')
  const base = ctx.createRadialGradient(w * 0.4, h * 0.4, 20, w / 2, h / 2, w * 0.52)
  base.addColorStop(0, '#e08860'); base.addColorStop(0.4, '#cc6040'); base.addColorStop(0.7, '#a84028'); base.addColorStop(1, '#702018')
  ctx.fillStyle = base; ctx.fillRect(0, 0, w, h)
  for (let i = 0; i < 35; i++) {
    ctx.fillStyle = `rgba(150,70,40,${0.15 + Math.random() * 0.35})`
    ctx.beginPath(); ctx.arc(w * 0.05 + Math.random() * w * 0.9, h * 0.05 + Math.random() * h * 0.9, 4 + Math.random() * 30, 0, Math.PI * 2); ctx.fill()
  }
  ctx.fillStyle = 'rgba(220,205,195,0.22)'; ctx.fillRect(0, 0, w, h * 0.13)
  ctx.fillStyle = 'rgba(220,205,195,0.22)'; ctx.fillRect(0, h * 0.87, w, h * 0.13)
  return c
}

function generateJupiterTexture(w, h) {
  const c = document.createElement('canvas'); c.width = w; c.height = h; const ctx = c.getContext('2d')
  const bands = ['#c8a878', '#e8d8b8', '#d4b888', '#f0e0c0', '#c0a070', '#e0ccaa', '#b89868', '#e8d8b8', '#c8a878', '#d8c098']
  const bandH = h / bands.length
  bands.forEach((color, i) => { ctx.fillStyle = color; ctx.fillRect(0, Math.floor(i * bandH), w, Math.ceil(bandH) + 1) })
  for (let i = 0; i < 50; i++) {
    ctx.fillStyle = `rgba(${180 + Math.random() * 60},${120 + Math.random() * 60},${60 + Math.random() * 40},0.12)`
    ctx.beginPath(); ctx.ellipse(w * 0.05 + Math.random() * w * 0.9, h * 0.05 + Math.random() * h * 0.9, 15 + Math.random() * 55, 3 + Math.random() * 12, Math.random() * Math.PI, 0, Math.PI * 2); ctx.fill()
  }
  ctx.fillStyle = 'rgba(210,130,85,0.45)'; ctx.beginPath()
  ctx.ellipse(w * 0.55, h * 0.48, w * 0.07, h * 0.045, 0.1, 0, Math.PI * 2); ctx.fill()
  ctx.strokeStyle = 'rgba(180,100,60,0.55)'; ctx.lineWidth = 2.5
  ctx.beginPath(); ctx.ellipse(w * 0.55, h * 0.48, w * 0.07, h * 0.045, 0.1, 0, Math.PI * 2); ctx.stroke()
  return c
}

function generateNeptuneTexture(w, h) {
  const c = document.createElement('canvas'); c.width = w; c.height = h; const ctx = c.getContext('2d')
  const base = ctx.createLinearGradient(0, 0, 0, h)
  base.addColorStop(0, '#1e3f99'); base.addColorStop(0.25, '#2c55c4'); base.addColorStop(0.5, '#1f4ab0')
  base.addColorStop(0.75, '#193d99'); base.addColorStop(1, '#1e3f99')
  ctx.fillStyle = base; ctx.fillRect(0, 0, w, h)
  for (let y = 0; y < h; y += 7) {
    ctx.fillStyle = `rgba(90,145,230,${0.04 + Math.random() * 0.08})`
    ctx.fillRect(0, y, w, 3)
  }
  for (let i = 0; i < 15; i++) {
    ctx.fillStyle = `rgba(140,190,240,0.06)`
    ctx.beginPath(); ctx.ellipse(w * Math.random(), h * Math.random(), w * 0.05 + Math.random() * w * 0.15, h * 0.02 + Math.random() * h * 0.04, Math.random() * Math.PI, 0, Math.PI * 2); ctx.fill()
  }
  return c
}

function drawContinent(ctx, cx, cy, rw, rh) { ctx.beginPath(); ctx.ellipse(cx, cy, rw, rh, Math.random() * 0.5, 0, Math.PI * 2); ctx.fill() }

// ============================================================
// 真实行星贴图（Solar System Scope，CC BY 4.0，solarsystemscope.com/textures）
// 已压缩为 1024×512；贴图按类型共用，切勿在 disposePlanet 中销毁
// 回退到程序化贴图：把 USE_REAL_PLANET_TEXTURES 改成 false 即可
// ============================================================
const USE_REAL_PLANET_TEXTURES = true
const PLANET_TEXTURE_FILES = {
  earth: 'planets/earth.jpg',
  saturn: 'planets/saturn.jpg',
  moon: 'planets/moon.jpg',
  mars: 'planets/mars.jpg',
  jupiter: 'planets/jupiter.jpg',
  neptune: 'planets/neptune.jpg',
  star: 'planets/star.jpg',
  mercury: 'planets/mercury.jpg',
  venus: 'planets/venus.jpg',
  uranus: 'planets/uranus.jpg',
}
const SATURN_RING_FILE = 'planets/saturn_ring.png'
let sharedTextureLoader = null
const sharedTextureCache = {}

function sharedTexture(relPath) {
  if (sharedTextureCache[relPath]) return sharedTextureCache[relPath]
  if (!sharedTextureLoader) sharedTextureLoader = new THREE.TextureLoader()
  const tex = sharedTextureLoader.load(import.meta.env.BASE_URL + relPath)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.wrapS = THREE.RepeatWrapping
  tex.anisotropy = 4
  tex.userData.shared = true
  sharedTextureCache[relPath] = tex
  return tex
}

function createPlanetTexture(type) {
  if (USE_REAL_PLANET_TEXTURES && PLANET_TEXTURE_FILES[type]) {
    return sharedTexture(PLANET_TEXTURE_FILES[type])
  }
  const SIZE = 1024; let c
  switch (type) {
    case 'earth': c = generateEarthTexture(SIZE, SIZE / 2); break
    case 'saturn': c = generateSaturnTexture(SIZE, SIZE / 2); break
    case 'moon': c = generateMoonTexture(SIZE, SIZE / 2); break
    case 'star': c = generateStarTexture(SIZE, SIZE / 2); break
    case 'mars': c = generateMarsTexture(SIZE, SIZE / 2); break
    case 'jupiter': c = generateJupiterTexture(SIZE, SIZE / 2); break
    case 'neptune': c = generateNeptuneTexture(SIZE, SIZE / 2); break
    default: c = generateEarthTexture(SIZE, SIZE / 2)
  }
  const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.SRGBColorSpace; tex.wrapS = THREE.RepeatWrapping; tex.wrapU = THREE.RepeatWrapping
  return tex
}

const BOX_RING_COLORS = { 1: 0x4488ee, 2: 0x44aa66, 3: 0xbb9933, 4: 0xbb44bb, 5: 0x3388aa }
function boxRingColor(box) { return BOX_RING_COLORS[Math.min(Math.max(box || 1, 1), 5)] || BOX_RING_COLORS[1] }

// ============================================================
// Text layout
// ============================================================
function computeTextLayout(front, back) {
  textCanvas.width = window.innerWidth
  textCanvas.height = window.innerHeight
  textCtx.font = `bold ${FONT_SIZE}px "PingFang SC","Microsoft YaHei","Noto Sans SC",sans-serif`

  const maxWidth = textCanvas.width - TEXT_PAD_X * 2
  const frontLines = wrapLines(textCtx, front, maxWidth)
  const backLines = wrapLines(textCtx, back, maxWidth)

  let frontChars = 0
  frontLines.forEach(l => { frontChars += l.length })

  const layout = []
  let lineIdx = 0, y = 0

  frontLines.forEach(line => {
    layout.push({ text: line, y, isFront: true, lineIdx, charStart: totalLayoutChars(layout), charEnd: totalLayoutChars(layout) + line.length })
    y += LINE_HEIGHT; lineIdx++
  })

  layout.push({ text: '', y, isFront: false, lineIdx, isGap: true })
  y += LINE_HEIGHT; lineIdx++

  backLines.forEach(line => {
    layout.push({ text: line, y, isFront: false, lineIdx, charStart: totalLayoutChars(layout), charEnd: totalLayoutChars(layout) + line.length })
    y += LINE_HEIGHT; lineIdx++
  })

  const blockH = y
  const blockTop = Math.round((textCanvas.height - blockH) / 2)
  layout.forEach(l => {
    l.y += blockTop
    // 每行的横向位置按整行文本一次性算好，打字过程中不再变化
    if (!l.isGap) l.x = Math.round((textCanvas.width - textCtx.measureText(l.text).width) / 2)
  })

  textLayout = layout
  frontCharCount = frontChars
  backCharCount = totalLayoutChars(layout) - frontChars
}

function totalLayoutChars(layout) {
  let n = 0; for (const l of layout) { if (!l.isGap) n += l.text.length }; return n
}

function wrapLines(ctx, text, maxWidth) {
  const chars = [...text]; const lines = []; let cur = ''
  for (const ch of chars) {
    const test = cur + ch
    if (ctx.measureText(test).width > maxWidth && cur.length > 0) { lines.push(cur); cur = ch }
    else cur = test
  }
  if (cur) lines.push(cur)
  return lines.length ? lines : ['']
}

// ============================================================
// Render text canvas
// ============================================================
function renderText() {
  if (!textCtx) return
  textCtx.clearRect(0, 0, textCanvas.width, textCanvas.height)
  if (typingPhase === 'idle') return

  // Text colors: nearest visible planet → its palette, else neutral brights
  let bestPlanet = null
  let bestDist = Infinity
  for (const p of allPlanets) {
    if (p.state !== 'approach' && p.state !== 'staging') continue
    if (!p.mesh.visible) continue
    const dx = p.mesh.position.x
    const dy = p.mesh.position.y
    const d = dx * dx + dy * dy
    if (d < bestDist) { bestDist = d; bestPlanet = p }
  }
  const colors = bestPlanet?.textColors
    || { question: 'rgba(230,240,255,0.95)', answer: 'rgba(210,225,245,0.95)' }
  const qc = colors.question
  const ac = colors.answer

  textCtx.globalAlpha = TEXT_FADE
  textCtx.textBaseline = 'top'
  textCtx.strokeStyle = 'rgba(0,0,0,0.55)'
  textCtx.lineWidth = 2.5

  let charsLeft = revealedChars
  for (const line of textLayout) {
    if (line.isGap) continue
    if (charsLeft <= 0) break
    const cnt = Math.min(charsLeft, line.text.length)
    const visible = line.text.substring(0, cnt)
    if (visible.length > 0) {
      textCtx.fillStyle = line.isFront ? qc : ac
      // 位置按整行宽度定死，打字时只填充字符，不再逐帧重排版
      const x = line.x
      textCtx.strokeText(visible, x, line.y)
      textCtx.fillText(visible, x, line.y)
    }
    charsLeft -= cnt
  }

  textCtx.globalAlpha = 1

  // Cursor at last revealed position
  if ((typingPhase === 'front' || typingPhase === 'back') && revealedChars > 0 && Math.floor(Date.now() / 530) % 2 === 0) {
    let lastLine = null, lastVisible = '', rem = revealedChars
    for (const line of textLayout) {
      if (line.isGap) continue
      if (rem <= 0) break
      const cnt = Math.min(rem, line.text.length)
      lastVisible = line.text.substring(0, cnt); lastLine = line; rem -= cnt
    }
    if (lastLine && lastVisible) {
      const tw = textCtx.measureText(lastVisible).width
      textCtx.fillStyle = '#4ECDC4'
      textCtx.fillRect(lastLine.x + tw + 1, lastLine.y, 2, FONT_SIZE)
    }
  }

  // Pause cursor blinks at end of front text
  if (typingPhase === 'pause' && Math.floor(Date.now() / 530) % 2 === 0) {
    let lastLine = null, lastVisible = '', rem = frontCharCount
    for (const line of textLayout) {
      if (line.isGap) continue
      if (rem <= 0) break
      const cnt = Math.min(rem, line.text.length)
      lastVisible = line.text.substring(0, cnt); lastLine = line; rem -= cnt
    }
    if (lastLine && lastVisible) {
      const tw = textCtx.measureText(lastVisible).width
      textCtx.fillStyle = '#FFCA28'
      textCtx.fillRect(lastLine.x + tw + 1, lastLine.y, 2, FONT_SIZE)
    }
  }
}

// ============================================================
// Galaxy & planets
// ============================================================
function groupIntoGalaxies() {
  if (cards.length === 0) return []
  const map = {}
  cards.forEach(c => {
    const seg = (c.categoryPath || '').split('/')[0].trim() || '默认星系'
    if (!map[seg]) map[seg] = []
    map[seg].push(c)
  })
  return Object.entries(map).map(([name, gCards]) => ({ name, cards: gCards }))
}

function createPlanetRingMesh(type) {
  if (!PLANET_RING[type]) return null

  // 真实环贴图：一张径向渐变条（u=内外半径），用 RingGeometry 按半径重写 UV
  if (USE_REAL_PLANET_TEXTURES && type === 'saturn') {
    const inner = 1.24, outer = 2.32
    const geo = new THREE.RingGeometry(inner, outer, 160, 1)
    const pos = geo.attributes.position
    const uv = geo.attributes.uv
    for (let i = 0; i < pos.count; i++) {
      const r = Math.hypot(pos.getX(i), pos.getY(i))
      uv.setXY(i, (r - inner) / (outer - inner), 0.5)
    }
    uv.needsUpdate = true
    const mat = new THREE.MeshBasicMaterial({
      map: sharedTexture(SATURN_RING_FILE),
      transparent: true,
      side: THREE.DoubleSide,
      depthWrite: false,
      opacity: 0.95,
    })
    const mesh = new THREE.Mesh(geo, mat)
    mesh.rotation.x = Math.PI / 2 - 0.22
    mesh.userData.texturedRing = true   // 环面已定型，不再做 rotation.z 晃动
    return mesh
  }

  const group = new THREE.Group()
  const makeTorus = (r, w, color, op, rot) => {
    const g = new THREE.TorusGeometry(r, w, 16, 120)
    const m = new THREE.MeshBasicMaterial({ color, transparent: true, opacity: op, side: THREE.DoubleSide })
    const mesh = new THREE.Mesh(g, m); mesh.rotation.x = rot; return mesh
  }
  group.add(makeTorus(1.3, 0.04, 0xd4b060, 0.4, Math.PI / 2.1))
  group.add(makeTorus(1.5, 0.02, 0xe8d080, 0.3, Math.PI / 2.12))
  group.add(makeTorus(1.7, 0.06, 0xc0a050, 0.25, Math.PI / 2.15))
  return group
}

// ============================================================
// 大气边缘辉光（Rayleigh/Mie 单次散射的视觉近似，参考 Maxime Heckel）
// 菲涅尔边缘 × 日照朝向：朝阳侧亮、背阳侧淡出；附加混合不遮挡球面细节
// 只给有大气层的行星，月球不加
// ============================================================
const ATMOSPHERE = {
  earth:   { color: 0x5b9dff, strength: 1.00 },
  saturn:  { color: 0xffe2a8, strength: 0.70 },
  jupiter: { color: 0xffd2a1, strength: 0.75 },
  neptune: { color: 0x6f93ff, strength: 1.00 },
  mars:    { color: 0xff9a62, strength: 0.55 },
  venus:   { color: 0xffd08a, strength: 0.55 },
  uranus:  { color: 0x9fe0ea, strength: 0.40 },
}
const SUN_DIR = new THREE.Vector3(15, 8, 20).normalize()

function createAtmosphereMesh(type) {
  const cfg = ATMOSPHERE[type]
  if (!cfg) return null

  const geo = new THREE.SphereGeometry(1.16, 32, 24)
  const mat = new THREE.ShaderMaterial({
    uniforms: {
      uColor: { value: new THREE.Color(cfg.color) },
      uStrength: { value: cfg.strength },
      uSun: { value: SUN_DIR },
    },
    vertexShader: `
      varying vec3 vN;
      varying vec3 vW;
      void main() {
        vN = normalize(mat3(modelMatrix) * normal);
        vec4 wp = modelMatrix * vec4(position, 1.0);
        vW = wp.xyz;
        gl_Position = projectionMatrix * viewMatrix * wp;
      }
    `,
    fragmentShader: `
      uniform vec3 uColor;
      uniform float uStrength;
      uniform vec3 uSun;
      varying vec3 vN;
      varying vec3 vW;
      void main() {
        vec3 V = normalize(cameraPosition - vW);
        // 背面球壳：用 |dot| 取边缘环带；再乘一项让最外缘淡出，避免硬边
        float d = abs(dot(normalize(vN), V));
        float r = 1.0 - d;
        float rim = r * r * (1.0 - r * r * r);
        // 朝阳面散射更强，背阳侧几乎消失
        float lit = clamp(dot(normalize(vN), uSun) * 0.5 + 0.5, 0.0, 1.0);
        float a = rim * uStrength * (0.05 + 0.95 * lit * lit) * 0.5;
        gl_FragColor = vec4(uColor, a);
      }
    `,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    side: THREE.BackSide,
  })
  const mesh = new THREE.Mesh(geo, mat)
  mesh.renderOrder = 1
  return mesh
}

function createPlanetMesh(type) {
  const tex = createPlanetTexture(type)
  const geo = new THREE.SphereGeometry(1, 96, 96)
  if (type === 'star') {
    const mat = new THREE.MeshBasicMaterial({ map: tex, transparent: true })
    return new THREE.Mesh(geo, mat)
  }
  const mat = new THREE.MeshStandardMaterial({ map: tex, roughness: 0.65, metalness: 0.05 })
  const mesh = new THREE.Mesh(geo, mat)
  const atmo = createAtmosphereMesh(type)   // 作为子节点跟随缩放/位置，无需逐帧同步
  if (atmo) mesh.add(atmo)
  return mesh
}

// ============================================================
// 主角星球高清贴图：屏幕同时只有一个放大的行星，其余背景只用 1K 缩略图
// 因此 2K 只在主角阶段加载，退回轨道立即释放，显存峰值约 1×8MB
// ============================================================
const HERO_TEXTURE_FILES = {
  earth: 'planets/2k/earth.jpg',
  saturn: 'planets/2k/saturn.jpg',
  moon: 'planets/2k/moon.jpg',
  mars: 'planets/2k/mars.jpg',
  jupiter: 'planets/2k/jupiter.jpg',
  neptune: 'planets/2k/neptune.jpg',
  star: 'planets/2k/star.jpg',
  mercury: 'planets/2k/mercury.jpg',
  venus: 'planets/2k/venus.jpg',
  uranus: 'planets/2k/uranus.jpg',
}
// 4K 纯享模式（会员）：仅主角星球使用，单张显存约 32MB
// 海王星没有 4K 源图，回落到 2K
const HERO_TEXTURE_FILES_4K = {
  earth: 'planets/4k/earth.jpg',
  saturn: 'planets/4k/saturn.jpg',
  moon: 'planets/4k/moon.jpg',
  mars: 'planets/4k/mars.jpg',
  jupiter: 'planets/4k/jupiter.jpg',
  star: 'planets/4k/star.jpg',
  mercury: 'planets/4k/mercury.jpg',
  venus: 'planets/4k/venus.jpg',
}
let quality4k = false
let heroTexGen = 0
let heroTextureLoader = null

function ensureHeroTexture(p) {
  if (p.heroTex || p.heroLoading) return
  const file = (quality4k ? (HERO_TEXTURE_FILES_4K[p.type] || HERO_TEXTURE_FILES[p.type]) : HERO_TEXTURE_FILES[p.type])
  if (!file) return
  if (!heroTextureLoader) heroTextureLoader = new THREE.TextureLoader()
  const gen = heroTexGen
  p.heroLoading = true
  const tex = heroTextureLoader.load(import.meta.env.BASE_URL + file, () => {
    p.heroLoading = false
    // 分辨率已切换或加载完成前已退回轨道：直接丢弃
    if (gen !== heroTexGen || p.state !== 'approach') { tex.dispose(); return }
    p.heroTex = tex
    p.mesh.material.map = tex
    p.mesh.material.needsUpdate = true
  })
  tex.colorSpace = THREE.SRGBColorSpace
  tex.anisotropy = 4
}

function releaseHeroTexture(p) {
  if (!p.heroTex) return
  p.mesh.material.map = p.thumbTex
  p.mesh.material.needsUpdate = true
  p.heroTex.dispose()
  p.heroTex = null
}

// 4K 纯享模式：切换当前主角贴图的分辨率，并进入/退出全屏沉浸
let immersiveHintTimer = null

function showImmersiveHint() {
  const el = document.getElementById('immersiveHint')
  if (!el) return
  el.classList.add('show')
  clearTimeout(immersiveHintTimer)
  immersiveHintTimer = setTimeout(() => el.classList.remove('show'), 4200)
}

function toggleQuality4K() {
  quality4k = !quality4k
  heroTexGen++
  const btn = document.getElementById('qualityToggle')
  if (btn) {
    btn.classList.toggle('active', quality4k)
    btn.title = quality4k ? '4K 纯享模式已开启（会员）' : '4K 纯享模式（会员）'
  }
  // 沉浸模式：隐藏 HUD/目录/模式/翻页弧视觉，仅保留 4K 标识
  document.body.classList.toggle('immersive-4k', quality4k)
  if (quality4k) showImmersiveHint()
  else {
    clearTimeout(immersiveHintTimer)
    const hint = document.getElementById('immersiveHint')
    if (hint) hint.classList.remove('show')
  }
  for (const p of allPlanets) {
    if (p.heroTex || p.heroLoading) {
      p.heroLoading = false
      releaseHeroTexture(p)
    }
  }
}

function createGlowMesh(type) {
  if (type === 'star') {
    // 真实太阳贴图自带亮度，旧的橙色球壳在特写下会露出硬边圆盘，已停用
    const g = new THREE.SphereGeometry(1.18, 32, 32)
    const m = new THREE.MeshBasicMaterial({ color: 0xffaa30, transparent: true, opacity: 0, depthWrite: false })
    return new THREE.Mesh(g, m)
  }
  // 行星的旧光晕已由真实大气边缘辉光接管，这里保留对象但不再绘制，
  // 避免外层可见性/缩放逻辑改动（恒星仍保留自身日冕）
  const g = new THREE.SphereGeometry(1.15, 32, 32)
  const m = new THREE.MeshBasicMaterial({ color: 0x2244aa, transparent: true, opacity: 0.1, depthWrite: false })
  m.visible = false
  return new THREE.Mesh(g, m)
}

// 不规则星云贴图：大量随机小光斑叠加，避免出现规整的圆形光晕
function createNebulaTexture() {
  const S = 512
  const c = document.createElement('canvas')
  c.width = c.height = S
  const g = c.getContext('2d')
  g.globalCompositeOperation = 'lighter'
  for (let i = 0; i < 90; i++) {
    // 横向分布：沿 X 拉长，Y 方向压缩，形成一条云带而不是圆团
    const x = S / 2 + (Math.random() * 2 - 1) * S * 0.38
    const y = S / 2 + (Math.random() * 2 - 1) * S * 0.10 + Math.sin(x / S * Math.PI * 2) * S * 0.04
    const r = 16 + Math.random() * 62
    const a = 0.025 + Math.random() * 0.06
    const grd = g.createRadialGradient(x, y, 0, x, y, r)
    grd.addColorStop(0, `rgba(255,255,255,${a})`)
    grd.addColorStop(1, 'rgba(255,255,255,0)')
    g.fillStyle = grd
    g.beginPath()
    g.arc(x, y, r, 0, Math.PI * 2)
    g.fill()
  }
  // 收边：整体径向衰减，保证外缘完全透明，不出现方形/硬边
  g.globalCompositeOperation = 'destination-in'
  g.save()
  g.translate(S / 2, S / 2)
  g.scale(1, 0.34)              // 压扁成横向椭圆
  const mask = g.createRadialGradient(0, 0, 0, 0, 0, S / 2)
  mask.addColorStop(0, 'rgba(255,255,255,1)')
  mask.addColorStop(0.55, 'rgba(255,255,255,0.7)')
  mask.addColorStop(1, 'rgba(255,255,255,0)')
  g.fillStyle = mask
  g.fillRect(-S / 2, -S / 2, S, S)
  g.restore()

  const t = new THREE.CanvasTexture(c)
  t.colorSpace = THREE.SRGBColorSpace
  return t
}

function createGalaxyNebula(galaxyName) {
  const group = new THREE.Group()

  // Central glow
  const glowGeo = new THREE.SphereGeometry(1.5, 32, 32)
  const glowMat = new THREE.MeshBasicMaterial({ color: 0x334488, transparent: true, opacity: 0.18, depthWrite: false })
  const glow = new THREE.Mesh(glowGeo, glowMat); group.add(glow)

  // Particle ring
  const ringGeo = new THREE.TorusGeometry(4, 0.15, 8, 80)
  const ringMat = new THREE.MeshBasicMaterial({ color: 0x4466aa, transparent: true, opacity: 0.12, depthWrite: false })
  const ring = new THREE.Mesh(ringGeo, ringMat); ring.rotation.x = Math.PI / 2.5; group.add(ring)

  // Second ring
  const ring2Geo = new THREE.TorusGeometry(5.5, 0.08, 8, 60)
  const ring2Mat = new THREE.MeshBasicMaterial({ color: 0x5577cc, transparent: true, opacity: 0.08, depthWrite: false })
  const ring2 = new THREE.Mesh(ring2Geo, ring2Mat); ring2.rotation.x = Math.PI / 2.8; group.add(ring2)

  // Particles around center
  const ptGeo = new THREE.BufferGeometry()
  const ptCount = 200; const ptPos = new Float32Array(ptCount * 3)
  for (let i = 0; i < ptCount; i++) {
    const a = Math.random() * Math.PI * 2; const r = 2 + Math.random() * 6
    ptPos[i * 3] = Math.cos(a) * r; ptPos[i * 3 + 1] = (Math.random() - 0.5) * 2; ptPos[i * 3 + 2] = Math.sin(a) * r * 0.4
  }
  ptGeo.setAttribute('position', new THREE.BufferAttribute(ptPos, 3))
  const ptMat = new THREE.PointsMaterial({ color: 0x6688cc, size: 0.08, transparent: true, opacity: 0.5, blending: THREE.AdditiveBlending, depthWrite: false })
  const pts = new THREE.Points(ptGeo, ptMat); group.add(pts)

  // 彩色星云：不规则云团贴图叠加，颜色渐变过渡，不出现独立圆形光晕
  const nebulaTex = createNebulaTexture()
  const clouds = [
    { color: 0x7f63ff, w: 18, h: 6.4, opacity: 0.17, x: 0.2, y: 0.2, z: -0.7, rot: 0 },
    { color: 0xff5f86, w: 12, h: 4.4, opacity: 0.13, x: -2.4, y: -0.7, z: -0.5, rot: 0.1 },
    { color: 0x3fd6d6, w: 8,  h: 3.0, opacity: 0.09, x: 2.8, y: 0.6, z: -0.2, rot: 0 },
  ]
  clouds.forEach((c) => {
    const sp = new THREE.Sprite(new THREE.SpriteMaterial({
      map: nebulaTex, color: c.color, blending: THREE.AdditiveBlending,
      transparent: true, opacity: c.opacity, depthWrite: false,
    }))
    sp.scale.set(c.w, c.h, 1)
    sp.material.rotation = c.rot
    sp.position.set(c.x, c.y, c.z)
    group.add(sp)
  })

  group.position.set(0, 0, -28)
  return group
}

function pickUniqueTypes(count) {
  const pool = [...PLANET_TYPES]
  for (let i = pool.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [pool[i], pool[j]] = [pool[j], pool[i]] }
  const result = []
  let prev = ''
  for (let i = 0; i < count; i++) {
    let pick = pool[i % pool.length]
    if (pick === prev && pool.length > 1) pick = pool[(i + 1) % pool.length]
    result.push(pick); prev = pick
  }
  return result
}

function createGalaxyPlanets(galaxy) {
  const gCards = galaxy.cards
  const count = Math.min(gCards.length, MAX_VISIBLE_PLANETS)
  const types = pickUniqueTypes(count)
  const planets = []
  const centerZ = -28

  for (let i = 0; i < count; i++) {
    const card = gCards[i]
    const type = types[i]

    const mesh = createPlanetMesh(type)
    const glow = createGlowMesh(type)
    const ring = createPlanetRingMesh(type)

    const angle = (i / count) * Math.PI * 2 + Math.random() * 0.3
    const radius = 5 + Math.random() * 4.5
    const x = Math.cos(angle) * radius
    const y = Math.sin(angle) * radius * 0.45
    const z = centerZ + (Math.random() - 0.5) * 6

    mesh.position.set(x, y, z)
    mesh.scale.setScalar(0.22 + Math.random() * 0.18)
    glow.position.copy(mesh.position); glow.scale.setScalar(type === 'star' ? 0.45 : 0.28)
    if (ring) { ring.position.copy(mesh.position); ring.scale.setScalar(0.25) }

    scene.add(mesh); scene.add(glow)
    if (ring) scene.add(ring)

    const ringColor = boxRingColor(card.box)
    glow.material.color.set(type === 'star' ? 0xffaa30 : ringColor)

    planets.push({
      mesh, glow, ring, card, type,
      thumbTex: mesh.material.map,   // 背景缩略贴图（1K），主角时临时换成 2K
      textColors: PLANET_TEXT_COLORS[type],
      orbitX: x, orbitY: y, orbitZ: z,
      orbitAngle: angle, orbitRadius: radius,
      orbitDir: Math.random() > 0.5 ? 1 : -1,           // random CW/CCW
      approachX: (Math.random() - 0.5) * 5,              // wider spread [-2.5, +2.5]
      approachY: -0.2 + Math.random() * 2.0,              // more vertical drift
      state: 'orbit',
      // Random trajectory: sweep from random side (most), some center
      trajectoryType: (() => {
        const r = Math.random()
        if      (r < 0.20) return 'sweep_left'
        else if (r < 0.40) return 'sweep_right'
        else if (r < 0.60) return 'sweep_top'
        else if (r < 0.80) return 'sweep_bottom'
        else               return 'center'
      })(),
      originScale: mesh.scale.x,
      scaleMult: 18
    })
  }

  return planets
}

// ============================================================
// Planet staging & sweeping — clean stage model: 1 hero, 1 hidden backup
// ============================================================
const MAIN_Z     = -4.0         // main planet z — max size (~60% screen)
const OFFSCREEN  = 12           // x/y position off-screen (hidden at MAIN_Z, visible at deep z as tiny dot)
const EXIT_PAST  = 15           // position past which planet is truly gone (radius ~5.8 + screen edge ~8 + margin)

function sweepScale(z, p) {
  const t = (z + 28) / 32
  return p.originScale + Math.pow(Math.max(t, 0), 1.6) * (p.scaleMult || 18)
}

function stagePlanet(forceIdx = -1) {
  if (allPlanets.length === 0) return null

  // Pick an orbit planet (prefer sweep trajectory)
  let orbitIdx = -1
  if (forceIdx >= 0 && allPlanets[forceIdx] &&
      (allPlanets[forceIdx].state === 'orbit' || allPlanets[forceIdx].state === 'staging')) {
    orbitIdx = forceIdx
  } else {
    for (let i = 0; i < allPlanets.length; i++) {
      const idx = (currentCardIdx + i) % allPlanets.length
      const p = allPlanets[idx]
      if (p.state !== 'orbit') continue
      if (p.trajectoryType && p.trajectoryType.startsWith('sweep')) { orbitIdx = idx; break }
      if (orbitIdx < 0) orbitIdx = idx
    }
  }
  if (orbitIdx < 0) return null

  const p = allPlanets[orbitIdx]
  p.state = 'staging'

  // Trajectory: use planet's own, or random sweep/diagonal
  let traj = p.trajectoryType
  if (!traj || traj === 'center') {
    const dirs = ['sweep_left', 'sweep_right', 'sweep_top', 'sweep_bottom',
                  'diag_tr', 'diag_tl', 'diag_br', 'diag_bl']
    traj = dirs[Math.floor(Math.random() * dirs.length)]
  }
  p._traj = traj

  // Random offset for variety
  const ox = (Math.random() - 0.5) * 6
  const oy = (Math.random() - 0.5) * 5

  // Set entry (off-screen) / exit / motion axis
  switch (traj) {
    case 'sweep_right':  p._ex = -OFFSCREEN; p._xx =  OFFSCREEN; p._ey = oy;  p._xy = oy;  p._axis = 'x'; p._dir =  1; break
    case 'sweep_left':   p._ex =  OFFSCREEN; p._xx = -OFFSCREEN; p._ey = oy;  p._xy = oy;  p._axis = 'x'; p._dir = -1; break
    case 'sweep_bottom': p._ey =  OFFSCREEN; p._xy = -OFFSCREEN; p._ex = ox;  p._xx = ox;  p._axis = 'y'; p._dir = -1; break
    case 'sweep_top':    p._ey = -OFFSCREEN; p._xy =  OFFSCREEN; p._ex = ox;  p._xx = ox;  p._axis = 'y'; p._dir =  1; break
    case 'diag_tr': // bottom-left → top-right
      p._ex = -10; p._ey = -7; p._xx = 10; p._xy = 7; p._axis = 'xy'; p._dir = 1; p._dy = 0.7; break
    case 'diag_tl': // bottom-right → top-left
      p._ex = 10; p._ey = -7; p._xx = -10; p._xy = 7; p._axis = 'xy'; p._dir = -1; p._dy = 0.7; break
    case 'diag_br': // top-left → bottom-right
      p._ex = -10; p._ey = 7; p._xx = 10; p._xy = -7; p._axis = 'xy'; p._dir = 1; p._dy = -0.7; break
    case 'diag_bl': // top-right → bottom-left
      p._ex = 10; p._ey = 7; p._xx = -10; p._xy = -7; p._axis = 'xy'; p._dir = -1; p._dy = -0.7; break
    default:
      p._ex = -OFFSCREEN; p._xx = OFFSCREEN; p._ey = 0; p._xy = 0; p._axis = 'x'; p._dir = 1
  }

  // Position off-screen at random entry depth — smaller at edge, grows during sweep
  // Probabilistic entry size: most medium, some tiny, some larger
  const rZ = Math.random()
  if (rZ < 0.20)      p._zStart = -12    // tiny entry  (~16%)
  else if (rZ < 0.55) p._zStart = -9     // moderate    (~25%)
  else if (rZ < 0.80) p._zStart = -7     // medium      (~34%)
  else                p._zStart = -5     // larger      (~48%)

  // Random max scale cap: 5-9x
  p._maxScale = 5 + Math.random() * 4

  const scale = sweepScale(p._zStart, p)
  p.mesh.position.set(p._ex, p._ey, p._zStart)
  p.mesh.scale.setScalar(scale)
  p.glow.scale.setScalar(scale * 1.2)
  if (p.ring) p.ring.scale.setScalar(scale)

  p.mesh.visible = false
  p.glow.visible = false
  if (p.ring) p.ring.visible = false
  p.mesh.material.transparent = false
  p.mesh.material.opacity = 1

  p.glow.position.copy(p.mesh.position)
  if (p.ring) p.ring.position.copy(p.mesh.position)

  return p
}

function updateHUDForPlanet(idx) {
  const n = idx + 1
  document.getElementById('hudCardNum').textContent = String(n).padStart(2, '0')
  document.getElementById('hudCardTotal').textContent = String(cards.length).padStart(2, '0')
}

// ============================================================
// Read mode — no planets, auto-advance
// ============================================================
function enterReadMode() {
  readMode = true
  readModePaused = false
  readModeTimer = 0
  readModeDelay = 0

  // Update mode button
  const btn = document.getElementById('modeToggle')
  if (btn) { btn.textContent = '📖' }

  // Hide all planets
  for (const p of allPlanets) {
    p.mesh.visible = false
    p.glow.visible = false
    if (p.ring) p.ring.visible = false
  }

  // Start first card immediately
  currentCardIdx = 0
  startCard()
}

function exitReadMode() {
  readMode = false
  readModePaused = false
  readModeTimer = 0

  // Update mode button
  const btn = document.getElementById('modeToggle')
  if (btn) { btn.textContent = '🪐' }

  // Show all planets & restart sweep system
  // Make orbit background planets visible
  for (const p of allPlanets) {
    if (p.state === 'orbit') {
      p.mesh.position.set(p.orbitX, p.orbitY, p.orbitZ)
      p.mesh.scale.setScalar(p.originScale)
      p.glow.scale.setScalar(p.originScale * 1.2)
      if (p.ring) p.ring.scale.setScalar(p.originScale)
      p.mesh.visible = true
      p.glow.visible = true
      if (p.ring) p.ring.visible = true
    }
  }
  // Reset any non-orbit planet back to orbit
  for (const p of allPlanets) {
    if (p.state !== 'orbit') {
      p.state = 'orbit'
      p.mesh.position.set(p.orbitX, p.orbitY, p.orbitZ)
      p.mesh.scale.setScalar(p.originScale)
      p.glow.position.copy(p.mesh.position); p.glow.scale.setScalar(p.originScale * 1.2)
      if (p.ring) { p.ring.position.copy(p.mesh.position); p.ring.scale.setScalar(p.originScale) }
      p.mesh.visible = true; p.glow.visible = true
      if (p.ring) p.ring.visible = true
      p.mesh.material.transparent = false; p.mesh.material.opacity = 1
    }
  }
  stagedBackup = null
  entranceGap = 0
  entranceGapTarget = 0
  const p = stagePlanet()
  if (p) {
    p.state = 'approach'
    p.mesh.visible = true
    p.glow.visible = true
    if (p.ring) p.ring.visible = true
  }

  // Restart the current card so card text resumes in planet mode
  startCard()
}

// ============================================================
// Cloud mode — fly-through 3D cloud field
// Same effect as the reference login page: thousands of misty
// cloud planes + distance fog, camera flies forward through them.
// ============================================================
const CLOUD_COUNT = 8000
const CLOUD_FOG_COLOR = 0x1d6fc3   // 与天空渐变中下部同色，远云融入蓝天

// Original cloud sprite from the reference page (256×256, mostly transparent)
const cloudTextureImage = new Image()
cloudTextureImage.src = cloudTextureUrl

// Bake 16000 rotated cloud quads (2 identical sets, 8000 apart) into one geometry
function buildCloudGeometry() {
  const HALF = 32
  const uvsQuad = [[0, 0], [1, 0], [1, 1], [0, 1]]
  const corners = [[-1, -1], [1, -1], [1, 1], [-1, 1]]
  const quads = CLOUD_COUNT * 2
  const positions = new Float32Array(quads * 4 * 3)
  const uvs = new Float32Array(quads * 4 * 2)
  const indices = new Uint32Array(quads * 6)

  let v = 0, k = 0
  for (let copy = 0; copy < 2; copy++) {
    for (let i = 0; i < CLOUD_COUNT; i++) {
      const cx = Math.random() * 1000 - 500
      const cy = -Math.random() * Math.random() * 200 - 15
      const cz = i - copy * 8000
      const rot = Math.random() * Math.PI
      const s = (Math.random() * Math.random() * 1.5 + 0.5) * HALF
      const cos = Math.cos(rot) * s
      const sin = Math.sin(rot) * s
      for (let q = 0; q < 4; q++) {
        const px = corners[q][0]
        const py = corners[q][1]
        positions[v * 3] = cx + px * cos - py * sin
        positions[v * 3 + 1] = cy + px * sin + py * cos
        positions[v * 3 + 2] = cz
        uvs[v * 2] = uvsQuad[q][0]
        uvs[v * 2 + 1] = uvsQuad[q][1]
        v++
      }
      const b = v - 4
      indices[k++] = b; indices[k++] = b + 1; indices[k++] = b + 2
      indices[k++] = b; indices[k++] = b + 2; indices[k++] = b + 3
    }
  }

  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geo.setAttribute('uv', new THREE.BufferAttribute(uvs, 2))
  geo.setIndex(new THREE.BufferAttribute(indices, 1))
  geo.computeBoundingSphere()
  return geo
}

function initCloudScene() {
  if (cloudRenderer) return true
  cloudCanvas = document.getElementById('cloudCanvas')
  cloudRenderer = new THREE.WebGLRenderer({ canvas: cloudCanvas, antialias: false, alpha: true })
  cloudRenderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
  cloudRenderer.setClearColor(0x000000, 0)

  cloudScene = new THREE.Scene()

  // The source sprite is a small 256px photo with baked-in grain; upscale + soften it
  // so magnified quads stay smooth instead of showing pixel speckles
  const texCanvas = document.createElement('canvas')
  texCanvas.width = texCanvas.height = 512
  const texCtx = texCanvas.getContext('2d')
  const tex = new THREE.CanvasTexture(texCanvas)
  // 着色器直接输出 sRGB 数值，贴图不做色彩空间转换，避免云层偏暗
  tex.colorSpace = THREE.NoColorSpace
  tex.minFilter = THREE.LinearMipmapLinearFilter
  tex.magFilter = THREE.LinearFilter

  const paintCloudTexture = () => {
    texCtx.clearRect(0, 0, 512, 512)
    texCtx.imageSmoothingEnabled = true
    texCtx.imageSmoothingQuality = 'high'
    texCtx.filter = 'blur(1.6px) brightness(1.06)'
    texCtx.drawImage(cloudTextureImage, 0, 0, 512, 512)
    tex.needsUpdate = true
  }
  if (cloudTextureImage.complete) paintCloudTexture()
  else cloudTextureImage.addEventListener('load', paintCloudTexture, { once: true })

  // Fragment shader mirrors the reference page: distance fog + depth-based
  // alpha so close quads fade out instead of stacking into a white wall
  const mat = new THREE.ShaderMaterial({
    uniforms: {
      map: { value: tex },
      fogColor: { value: new THREE.Color().setHex(CLOUD_FOG_COLOR, THREE.NoColorSpace) },
      fogNear: { value: -100 },
      fogFar: { value: 3000 },
    },
    vertexShader: `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: `
      uniform sampler2D map;
      uniform vec3 fogColor;
      uniform float fogNear;
      uniform float fogFar;
      varying vec2 vUv;
      void main() {
        float depth = gl_FragCoord.z / gl_FragCoord.w;
        float fogFactor = smoothstep(fogNear, fogFar, depth);
        vec4 c = texture2D(map, vUv);
        c.rgb = mix(c.rgb, vec3(1.0), 0.9);
        c.w *= pow(gl_FragCoord.z, 20.0);
        gl_FragColor = mix(c, vec4(fogColor, c.w), fogFactor);
      }
    `,
    transparent: true,
    depthTest: false,
    depthWrite: false,
  })

  cloudScene.add(new THREE.Mesh(buildCloudGeometry(), mat))

  cloudCamera = new THREE.PerspectiveCamera(30, window.innerWidth / window.innerHeight, 1, 3000)
  cloudCamera.position.set(0, 70, 6000)

  resizeCloud()
  return true
}

function resizeCloud() {
  if (!cloudRenderer || !cloudCamera) return
  const w = window.innerWidth
  const h = window.innerHeight
  cloudRenderer.setSize(w, h)
  cloudCamera.aspect = w / h
  cloudCamera.updateProjectionMatrix()
}

function animateClouds() {
  cloudAnimId = requestAnimationFrame(animateClouds)
  if (!cloudRenderer) return

  const now = performance.now()
  if (!cloudStartTime) cloudStartTime = now

  // Camera flies forward through the cloud field, looping every 8000 units
  const pos = ((now - cloudStartTime) * 0.03) % 8000

  // Gentle automatic sway (works without a mouse, e.g. in the app WebView)
  const t = now * 0.001
  const targetX = Math.sin(t * 0.22) * 30
  // 相机整体略微上移（视线方向保持不变，云层自然落在画面下方）
  const targetY = 70 + Math.cos(t * 0.17) * 8
  cloudCamera.position.x += (targetX - cloudCamera.position.x) * 0.02
  cloudCamera.position.y += (targetY - cloudCamera.position.y) * 0.02
  cloudCamera.position.z = -pos + 8000
  cloudCamera.lookAt(cloudCamera.position.x, cloudCamera.position.y, cloudCamera.position.z - 1000)

  cloudRenderer.render(cloudScene, cloudCamera)
}

function enterCloudMode() {
  cloudMode = true
  document.body.classList.add('light-scene')
  const btn = document.getElementById('modeToggle')
  if (btn) { btn.textContent = '☁️' }

  // Hide Three.js canvas only — keep card text, HUD and cockpit frame
  document.getElementById('starfield').classList.add('hidden')
  document.getElementById('cockpit').classList.remove('hidden')
  document.getElementById('hud').classList.remove('hidden')
  const tc = document.getElementById('textCanvas')
  if (tc) tc.classList.remove('hidden')

  // Show cloud canvas
  cloudCanvas.classList.remove('hidden')
  if (!initCloudScene()) return
  cloudStartTime = performance.now()
  resizeCloud()
  animateClouds()
}

function exitCloudMode() {
  cloudMode = false
  if (cloudAnimId) { cancelAnimationFrame(cloudAnimId); cloudAnimId = null }
  cloudStartTime = 0
  cloudCanvas.classList.add('hidden')

  // Show Three.js elements
  document.getElementById('starfield').classList.remove('hidden')
  document.getElementById('cockpit').classList.remove('hidden')
  document.getElementById('hud').classList.remove('hidden')
  const tc = document.getElementById('textCanvas')
  if (tc) tc.classList.remove('hidden')

  // Reset mode button to planet
  const btn2 = document.getElementById('modeToggle')
  if (btn2) { btn2.textContent = '🪐' }
  document.body.classList.remove('light-scene')
}

// ============================================================
// 3D mode — glowmarcher shader (Shadertoy t3GfDd by mrange, CC0)
// https://www.shadertoy.com/view/t3GfDd
// ============================================================
const SHADER_VERT = `
attribute vec2 aPos;
void main() { gl_Position = vec4(aPos, 0.0, 1.0); }
`

const SHADER_FRAG = `
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec3 iResolution;
uniform float iTime;

#define N normalize
#define V vec3

float roundTo(float x) { return floor(x + 0.5); }
float tanh1(float x) { float e = exp(2.0 * x); return (e - 1.0) / (e + 1.0); }
vec4 tanh4(vec4 x) { return vec4(tanh1(x.x), tanh1(x.y), tanh1(x.z), tanh1(x.w)); }

void mainImage(out vec4 fragColor, in vec2 C) {
  vec2 A = vec2(0.4, 0.3), s;
  V I = iResolution, S = V(s = sin(A * iTime), iTime);
  V Z = N(V(A * cos(A * S.z), 1.0));
  V p = N(cross(V(0.0, 1.0, 0.0) + V(A * A * s, 0.0), Z));
  C -= 0.5 * I.xy;
  I = N(C.y * cross(p, Z) + I.y * Z - C.x * p);
  vec4 O = vec4(0.0), o = vec4(0.0);
  float d = 0.0, z = 0.0;
  for (float i = 0.0; i < 77.0; i++) {
    p = z * I + S;
    p.xy -= s = sin(A * p.z);
    Z = N(V(A * cos(A * p.z), -1.0));
    p -= dot(p.xy, Z.xy) * 0.5 * Z;
    float a = 0.16 * s.x;
    mat2 R = mat2(vec2(cos(0.0 - a), cos(11.0 - a)), vec2(cos(33.0 - a), cos(0.0 - a)));
    p.xy *= R;
    o = 0.9 + sin(2.0 * abs(p.x) + vec4(8.0, 3.0, 4.0, 2.0));
    p -= vec3(roundTo(p.x), roundTo(p.y), roundTo(p.z));
    z += d = 0.001 + 0.7 * abs(0.53 - sqrt(dot(p, p)));
    O += o.w / d * o;
  }
  o = tanh4(O / 6e4);
  // 护眼绿「玉玻璃」：用原始明暗结构驱动通透的绿色，暗处透出背景黑
  float lum = clamp(max(o.g, o.b * 0.9) * 1.2, 0.0, 1.0);
  float a = pow(lum, 1.1);
  fragColor = vec4(vec3(0.22, 0.88, 0.62) * a, 1.0);
}

void main() {
  vec4 c;
  mainImage(c, gl_FragCoord.xy);
  gl_FragColor = c;
}
`

function compileShader(gl, type, source) {
  const shader = gl.createShader(type)
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    console.warn('3D shader compile failed:', gl.getShaderInfoLog(shader))
    gl.deleteShader(shader)
    return null
  }
  return shader
}

function initShader() {
  if (shaderGl) return true
  shaderCanvas = document.getElementById('shaderCanvas')
  const gl = shaderCanvas.getContext('webgl', {
    antialias: false, alpha: false, depth: false, stencil: false,
    preserveDrawingBuffer: false, powerPreference: 'high-performance'
  })
  if (!gl) {
    console.warn('WebGL unavailable, 3D mode cannot start')
    return false
  }

  const vs = compileShader(gl, gl.VERTEX_SHADER, SHADER_VERT)
  const fs = compileShader(gl, gl.FRAGMENT_SHADER, SHADER_FRAG)
  if (!vs || !fs) return false

  const program = gl.createProgram()
  gl.attachShader(program, vs)
  gl.attachShader(program, fs)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    console.warn('3D shader link failed:', gl.getProgramInfoLog(program))
    return false
  }

  gl.useProgram(program)
  const buffer = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW)
  const loc = gl.getAttribLocation(program, 'aPos')
  gl.enableVertexAttribArray(loc)
  gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0)
  gl.disable(gl.DEPTH_TEST)
  gl.disable(gl.BLEND)

  shaderUniforms = {
    resolution: gl.getUniformLocation(program, 'iResolution'),
    time: gl.getUniformLocation(program, 'iTime'),
  }
  shaderGl = gl
  resizeShader()
  return true
}

function resizeShader() {
  if (!shaderCanvas) return
  const dpr = Math.min(window.devicePixelRatio || 1, 1.25)
  const w = Math.max(1, Math.round(window.innerWidth * dpr * SHADER_SCALE))
  const h = Math.max(1, Math.round(window.innerHeight * dpr * SHADER_SCALE))
  if (shaderCanvas.width !== w || shaderCanvas.height !== h) {
    shaderCanvas.width = w
    shaderCanvas.height = h
  }
  if (shaderGl) shaderGl.viewport(0, 0, w, h)
}

function animateShader() {
  shaderAnimId = requestAnimationFrame(animateShader)
  if (!shaderGl) return
  resizeShader()
  const now = performance.now()
  const dt = Math.min(0.1, (now - shaderLastTs) / 1000)
  shaderLastTs = now
  // 档位 1 最慢（4 秒/张），档位 8 最快（0.5 秒/张），线性递减
  const ringSeconds = RING_SECONDS_SLOWEST - (RING_SECONDS_SLOWEST - RING_SECONDS_FASTEST) * (shaderLevel - 1) / 7
  if (!paused) shaderAccum += dt * (1 / ringSeconds)
  shaderGl.uniform3f(shaderUniforms.resolution, shaderCanvas.width, shaderCanvas.height, 1)
  shaderGl.uniform1f(shaderUniforms.time, shaderAccum)
  shaderGl.drawArrays(shaderGl.TRIANGLES, 0, 3)

  // 一个「环」翻一张卡片，与画面同一条时间轴，调速时自动同步
  if (paused) return
  const ring = Math.floor(shaderAccum / RING_PER_CARD)
  if (ring !== shaderRingIdx) {
    shaderRingIdx = ring
    currentCardIdx = (currentCardIdx + 1) % cards.length
    startCard()
    revealAllText()
  }
}

// 3D 模式不做逐字输出，直接整卡显示
function revealAllText() {
  typingPhase = 'done'
  revealedChars = totalLayoutChars(textLayout)
  pauseTimer = 0
  renderText()
}

function formatSpeed(level) { return `${level}×` }

function enterShaderMode() {
  if (!initShader()) return false
  shaderMode = true
  shaderAccum = 0
  shaderLastTs = performance.now()
  shaderRingIdx = 0

  const btn = document.getElementById('modeToggle')
  if (btn) {
    btn.textContent = '3D'
    btn.style.fontSize = '15px'
    btn.style.fontWeight = 'bold'
  }

  // 3D 背景显示，卡片文字与 HUD 保留
  document.getElementById('starfield').classList.add('hidden')
  document.getElementById('cockpit').classList.remove('hidden')
  document.getElementById('hud').classList.remove('hidden')
  const tc = document.getElementById('textCanvas')
  if (tc) tc.classList.remove('hidden')
  shaderCanvas.classList.remove('hidden')
  // 速度胶囊：只在 3D 模式显示
  const sp = document.getElementById('speedToggle')
  if (sp) { sp.classList.remove('hidden'); sp.textContent = formatSpeed(shaderLevel) }
  // 3D 直接整卡显示，不做逐字输出
  revealAllText()
  resizeShader()
  animateShader()
  return true
}

function exitShaderMode() {
  shaderMode = false
  if (shaderAnimId) { cancelAnimationFrame(shaderAnimId); shaderAnimId = null }

  if (shaderCanvas) shaderCanvas.classList.add('hidden')
  const sp = document.getElementById('speedToggle')
  if (sp) sp.classList.add('hidden')
  document.getElementById('starfield').classList.remove('hidden')
  document.getElementById('cockpit').classList.remove('hidden')
  document.getElementById('hud').classList.remove('hidden')
  const tc = document.getElementById('textCanvas')
  if (tc) tc.classList.remove('hidden')

  const btn = document.getElementById('modeToggle')
  if (btn) {
    btn.textContent = '🪐'
    btn.style.fontSize = ''
    btn.style.fontWeight = ''
  }

  // 回到常规模式：当前卡片重新走一遍打字机流程
  startCard()
}

// 模式循环：星球 → 阅读 → 白云 → 3D → 星球
function cycleMode() {
  if (shaderMode) exitShaderMode()
  else if (cloudMode) { exitCloudMode(); enterShaderMode() }
  else if (readMode) { exitReadMode(); enterCloudMode() }
  else enterReadMode()
  updateModeUI()
}

// ============================================================
// 模式弹层：围绕切换按钮展开，点击直接切换模式
// ============================================================
function currentModeKey() {
  return shaderMode ? '3d' : cloudMode ? 'cloud' : readMode ? 'read' : 'planet'
}

function updateModeUI() {
  const key = currentModeKey()
  const btn = document.getElementById('modeToggle')
  if (btn) {
    btn.textContent = key === '3d' ? '3D' : key === 'cloud' ? '☁️' : key === 'read' ? '📖' : '🪐'
    btn.style.fontSize = key === '3d' ? '15px' : ''
    btn.style.fontWeight = key === '3d' ? 'bold' : ''
  }
  document.querySelectorAll('.mode-item').forEach(el => {
    el.classList.toggle('active', el.dataset.mode === key)
  })
}

function setMode(target) {
  closeModeMenu()
  if (target === 'planet') {
    if (shaderMode) exitShaderMode()
    if (cloudMode) exitCloudMode()
    if (readMode) exitReadMode()
  } else if (target === 'read') {
    if (shaderMode) exitShaderMode()
    if (cloudMode) exitCloudMode()
    if (!readMode) enterReadMode()
  } else if (target === 'cloud') {
    if (shaderMode) exitShaderMode()
    if (readMode) exitReadMode()
    if (!cloudMode) enterCloudMode()
  } else if (target === '3d') {
    if (readMode) exitReadMode()
    if (cloudMode) exitCloudMode()
    if (!shaderMode) enterShaderMode()
  }
  updateModeUI()
  // 4K 纯享按钮只在星球模式出现（与 3D 档位同位置，互斥）
  const qb = document.getElementById('qualityToggle')
  if (qb) qb.classList.toggle('hidden', target !== 'planet')
}

function toggleModeMenu() {
  const menu = document.getElementById('modeMenu')
  const open = menu.classList.toggle('open')
  if (open) updateModeUI()
}

function closeModeMenu() {
  document.getElementById('modeMenu').classList.remove('open')
}

// 主角星球立即退回背景轨道（复用自然退场时的复位逻辑）
function resetPlanetToOrbit(p) {
  p.state = 'orbit'
  p.mesh.position.set(p.orbitX, p.orbitY, p.orbitZ)
  p.mesh.scale.setScalar(p.originScale)
  p.glow.position.copy(p.mesh.position)
  p.glow.scale.setScalar(p.originScale * 1.2)
  if (p.ring) {
    p.ring.position.copy(p.mesh.position)
    p.ring.scale.setScalar(p.originScale)
  }
  releaseHeroTexture(p)
}

// 手动翻页时立即换星球：跳过 10-20s 间隔与缓慢入场
// 轮询群星：每点一次换下一颗，不会重复回到刚退场的那颗
let planetCursor = 0

function forceHeroForCurrentCard() {
  if (allPlanets.length === 0) return
  const prev = allPlanets.find(p => p.state === 'approach') || null
  for (const p of allPlanets) {
    if (p.state === 'approach') resetPlanetToOrbit(p)
  }
  stagedBackup = null
  entranceGap = 0

  let p = null
  for (let k = 1; k <= allPlanets.length; k++) {
    const idx = (planetCursor + k) % allPlanets.length
    const cand = allPlanets[idx]
    if (cand === prev) continue
    // 巡游中的备用星球也可以直接上场
    if (cand.state === 'orbit' || cand.state === 'staging') {
      if (stagedBackup === cand) stagedBackup = null
      planetCursor = idx
      p = stagePlanet(idx)
      break
    }
  }
  // 只有一颗星球时，重新展示它自己
  if (!p) p = stagePlanet(allPlanets.indexOf(prev))
  if (!p) return
  // 直接出现在画面中前方（中等尺寸，避免点击瞬间满屏压迫），随后照常向外漂移
  p.mesh.position.set(0, 0, MAIN_Z - 15)
  const sc = Math.min(sweepScale(p.mesh.position.z, p), p._maxScale || 12)
  p.mesh.scale.setScalar(sc)
  p.glow.position.copy(p.mesh.position)
  p.glow.scale.setScalar(sc * 1.2)
  if (p.ring) {
    p.ring.position.copy(p.mesh.position)
    p.ring.scale.setScalar(sc)
  }
  p.mesh.visible = true
  p.glow.visible = true
  if (p.ring) p.ring.visible = true
  p.state = 'approach'
  ensureHeroTexture(p)
}

// 手动翻页（底部左右圆弧热区）
function goToCard(delta) {
  if (cards.length === 0) return
  currentCardIdx = (currentCardIdx + delta + cards.length) % cards.length
  startCard()
  if (shaderMode) {
    // 手动翻页后重新计时，让新卡片完整停留一个环
    shaderAccum = 0
    shaderRingIdx = 0
    revealAllText()
  } else if (quality4k && !readMode && !cloudMode) {
    // 仅 4K 沉浸模式：左右点击轮询群星；普通模式只切换卡片，不动星球
    forceHeroForCurrentCard()
  }
}

// ============================================================
// 目录（星系）切换
// ============================================================
function disposeObject3D(root) {
  root.traverse(o => {
    if (o.geometry) o.geometry.dispose()
    if (o.material) {
      // 真实行星贴图按类型共用，销毁单颗星球时不能释放
      if (o.material.map && !o.material.map.userData?.shared) o.material.map.dispose()
      o.material.dispose()
    }
  })
}

function disposePlanet(p) {
  scene.remove(p.mesh); scene.remove(p.glow)
  if (p.ring) scene.remove(p.ring)
  disposeObject3D(p.mesh)
  disposeObject3D(p.glow)
  if (p.ring) disposeObject3D(p.ring)
}

// ============================================================
// 目录选择 — 按知识体系 categoryPath 构建树
// ============================================================
let allCards = []
let categoryTree = null
let selectedFolderPath = ''

function buildCategoryTree(list) {
  const root = { name: '全部', path: '', depth: 0, cards: [], children: [] }
  const byPath = new Map([['', root]])

  list.forEach(card => {
    root.cards.push(card)
    const segs = (card.categoryPath || '').split('/').map(s => s.trim()).filter(Boolean)
    let parent = root
    let parentPath = ''
    segs.forEach(seg => {
      const path = parentPath ? `${parentPath}/${seg}` : seg
      let node = byPath.get(path)
      if (!node) {
        node = { name: seg, path, depth: parent.depth + 1, cards: [], children: [] }
        byPath.set(path, node)
        parent.children.push(node)
      }
      node.cards.push(card)
      parent = node
      parentPath = path
    })
  })

  return root
}

const expandedFolders = new Set([''])

function flattenVisible(node, out = []) {
  out.push(node)
  if (expandedFolders.has(node.path)) node.children.forEach(c => flattenVisible(c, out))
  return out
}

function toggleFolderNode(node) {
  if (expandedFolders.has(node.path)) expandedFolders.delete(node.path)
  else expandedFolders.add(node.path)
  renderFolderMenu()
}

function renderFolderMenu() {
  const menu = document.getElementById('folderMenu')
  menu.innerHTML = ''

  flattenVisible(categoryTree).forEach(node => {
    const expanded = expandedFolders.has(node.path)
    const b = document.createElement('button')
    b.className = 'folder-item' + (node.path === selectedFolderPath ? ' active' : '')
    b.style.paddingLeft = `${8 + node.depth * 12}px`

    // 层级多时用箭头折叠/展开，点行本身才是选择目录
    const chevron = document.createElement('span')
    chevron.className = 'folder-chevron'
    chevron.textContent = node.children.length ? (expanded ? '▾' : '▸') : ''
    if (node.children.length) {
      chevron.addEventListener('click', (ev) => {
        ev.stopPropagation()
        toggleFolderNode(node)
      })
    }

    const ico = document.createElement('span')
    ico.className = 'folder-ico'
    ico.textContent = node.children.length && expanded ? '📂' : '📁'

    const name = document.createElement('span')
    name.className = 'folder-name'
    name.textContent = node.name

    const count = document.createElement('span')
    count.className = 'folder-count'
    count.textContent = node.cards.length

    b.append(chevron, ico, name, count)
    b.addEventListener('click', (e) => {
      e.stopPropagation()
      selectedFolderPath = node.path
      closeFolderMenu()
      applyCardSet(node)
    })
    menu.appendChild(b)
  })

  const active = menu.querySelector('.folder-item.active')
  if (active) active.scrollIntoView({ block: 'nearest' })
}

function applyCardSet(node) {
  if (node.cards.length === 0) return
  cards = node.cards.slice()

  allPlanets.forEach(disposePlanet)
  allPlanets = createGalaxyPlanets({ name: node.name, cards })

  stagedBackup = null
  entranceGap = 0
  entranceGapTarget = 0
  currentCardIdx = 0
  document.getElementById('hudCardTotal').textContent = String(cards.length).padStart(2, '0')
  updateHudFolderName()
  startCard()

  if (shaderMode) {
    shaderAccum = 0
    shaderRingIdx = 0
    revealAllText()
  } else if (!readMode) {
    const p = stagePlanet()
    if (p) {
      p.state = 'approach'
      p.mesh.visible = true
      p.glow.visible = true
      if (p.ring) p.ring.visible = true
    }
  }
}

function toggleFolderMenu() {
  const menu = document.getElementById('folderMenu')
  const open = menu.classList.toggle('open')
  if (open) {
    closeModeMenu()
    renderFolderMenu()
  }
}

function closeFolderMenu() {
  document.getElementById('folderMenu').classList.remove('open')
}

// 退出星空记忆：App 内走原生桥，浏览器内退回到来源页
function exitStarfield() {
  const br = getBridge()
  if (br && typeof br.exitStarfield === 'function') br.exitStarfield()
  else history.back()
}

function startCard() {
  if (currentCardIdx >= cards.length) {
    // All cards done — loop back
    currentCardIdx = 0
  }

  const card = cards[currentCardIdx]
  computeTextLayout(card.front, card.back)
  if (readMode) {
    updateHUDForReadMode(currentCardIdx)
  } else {
    updateHUDForPlanet(currentCardIdx)
  }

  typingPhase = 'front'
  revealedChars = 0
  revealTimer = 0
  revealSpeed = 45
  TEXT_FADE = 1
  readModeTimer = 0
  readModeDelay = 0
  renderText()
}

function updateHUDForReadMode(idx) {
  const n = idx + 1
  document.getElementById('hudCardNum').textContent = String(n).padStart(2, '0')
  document.getElementById('hudCardTotal').textContent = String(cards.length).padStart(2, '0')
  updateHudFolderName()
}

// HUD 目录段只显示当前选中节点名字，点击弹出目录浮窗
function updateHudFolderName() {
  const el = document.getElementById('hudFolderName')
  if (!el) return
  const node = findCategoryNode(selectedFolderPath)
  el.textContent = node ? node.name : '全部'
  el.parentElement.title = selectedFolderPath || '全部'
}

function findCategoryNode(path) {
  if (!categoryTree) return null
  if (!path) return categoryTree
  let node = categoryTree
  for (const seg of path.split('/')) {
    node = node.children.find(c => c.name === seg)
    if (!node) return null
  }
  return node
}

function togglePlayPause() {
  paused = !paused
  const btn = document.getElementById('hudPlay')
  if (!btn) return
  btn.textContent = paused ? '▶' : '⏸'
  btn.title = paused ? '播放' : '暂停'
}

// ============================================================
// Init
// ============================================================
async function init() {
  scene = new THREE.Scene()

  camera = new THREE.PerspectiveCamera(50, window.innerWidth / window.innerHeight, 0.3, 150)
  camera.position.set(0, 1.2, 6)
  camera.lookAt(0, 0, -30)

  renderer = new THREE.WebGLRenderer({ canvas: document.getElementById('starfield'), antialias: true, alpha: false })
  renderer.setSize(window.innerWidth, window.innerHeight)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.2

  createBgStars()
  setupLights()

  textCanvas = document.getElementById('textCanvas')
  textCanvas.width = window.innerWidth
  textCanvas.height = window.innerHeight
  textCtx = textCanvas.getContext('2d')

  cloudCanvas = document.getElementById('cloudCanvas')

  await loadCards()
  if (cards.length === 0) {
    document.getElementById('loading').classList.add('hidden')
    document.getElementById('empty').classList.remove('hidden')
    return
  }

  cards.sort((a, b) => (a.box || 1) - (b.box || 1))

  // 保留完整卡片集，按知识体系构建目录树
  allCards = cards.slice()
  categoryTree = buildCategoryTree(allCards)
  // 默认展开根与一级目录，深层目录按需展开
  categoryTree.children.forEach(c => expandedFolders.add(c.path))

  // Build galaxies
  galaxies = groupIntoGalaxies()
  if (galaxies.length === 0) galaxies = [{ name: '记忆星系', cards }]

  document.getElementById('hudCardTotal').textContent = String(cards.length).padStart(2, '0')
  document.getElementById('loading').classList.add('hidden')
  updateHudFolderName()

  // Create nebula for first galaxy
  nebulaGroup = createGalaxyNebula(galaxies[galaxyIdx].name)
  scene.add(nebulaGroup)

  // Create all planets for first galaxy
  allPlanets = createGalaxyPlanets(galaxies[galaxyIdx])

  // Kick first planet and start first card
  currentCardIdx = 0
  startCard()
  updateModeUI()
  if (!readMode) {
    // Kick initial sweep (no gap for first entry)
    entranceGap = 0
    const p = stagePlanet()
    if (p) {
      p.state = 'approach'
      p.mesh.visible = true
      p.glow.visible = true
      if (p.ring) p.ring.visible = true
    }
  }

  window.addEventListener('resize', () => {
    camera.aspect = window.innerWidth / window.innerHeight
    camera.updateProjectionMatrix()
    renderer.setSize(window.innerWidth, window.innerHeight)
    textCanvas.width = window.innerWidth
    textCanvas.height = window.innerHeight
    if (shaderMode) resizeShader()
    if (cloudMode) resizeCloud()
    if (cards[currentCardIdx]) {
      computeTextLayout(cards[currentCardIdx].front, cards[currentCardIdx].back)
    }
    renderText()
  })

  // Click → toggle pause in read mode
  let lastClick = 0
  textCanvas.addEventListener('click', () => {
    closeModeMenu()
    closeFolderMenu()
    const now = Date.now()
    if (now - lastClick < 350) {
      // Double-click → cycle: planet → read → cloud → 3D → planet
      cycleMode()
      lastClick = 0
      return
    }
    lastClick = now
    if (readMode) {
      readModePaused = !readModePaused
    }
  })

  // Mode toggle button — 点击展开/收起模式弹层
  const modeBtn = document.getElementById('modeToggle')
  modeBtn.addEventListener('click', (e) => {
    e.stopPropagation()
    toggleModeMenu()
  })

  document.querySelectorAll('.mode-item').forEach(el => {
    el.addEventListener('click', (e) => {
      e.stopPropagation()
      setMode(el.dataset.mode)
    })
  })

  // 3D 翻页档位 — 圆形按钮，1–8 档循环，1 档 = 每张 0.5 秒
  const speedBtn = document.getElementById('speedToggle')
  speedBtn.addEventListener('click', (e) => {
    e.stopPropagation()
    shaderLevel = (shaderLevel % 8) + 1
    speedBtn.textContent = formatSpeed(shaderLevel)
  })

  // 底部两角圆弧 → 左右翻页
  document.getElementById('prevZone').addEventListener('click', (e) => {
    e.stopPropagation()
    goToCard(-1)
  })
  document.getElementById('nextZone').addEventListener('click', (e) => {
    e.stopPropagation()
    goToCard(1)
  })

  // 左下：选择目录（知识体系树）
  document.getElementById('folderToggle').addEventListener('click', (e) => {
    e.stopPropagation()
    toggleFolderMenu()
  })

  // HUD 目录段：显示当前节点名，点击弹出同一个目录浮窗
  document.getElementById('hudFolder').addEventListener('click', (e) => {
    e.stopPropagation()
    toggleFolderMenu()
  })

  // HUD 播放/暂停：二合一按钮
  document.getElementById('hudPlay').addEventListener('click', (e) => {
    e.stopPropagation()
    togglePlayPause()
  })

  // 右下：4K 纯享模式（会员）
  document.getElementById('qualityToggle').addEventListener('click', (e) => {
    e.stopPropagation()
    toggleQuality4K()
  })

  // 右上：退出星空记忆 —— 仅首次进入提示，5 秒后自动收起
  const exitBtn = document.getElementById('exitStarfield')
  const EXIT_HINT_KEY = 'starfield_exit_hint_seen'
  if (!localStorage.getItem(EXIT_HINT_KEY)) {
    exitBtn.classList.remove('hidden')
    setTimeout(() => {
      exitBtn.classList.add('hidden')
      localStorage.setItem(EXIT_HINT_KEY, '1')
    }, 5000)
  }
  exitBtn.addEventListener('click', (e) => {
    e.stopPropagation()
    exitBtn.classList.add('hidden')
    localStorage.setItem(EXIT_HINT_KEY, '1')
    exitStarfield()
  })

  // Keyboard: R → read/planet, C → cloud, D → 3D
  window.addEventListener('keydown', (e) => {
    if (shaderMode) {
      if (e.key === 'd' || e.key === 'D') exitShaderMode()
      return
    }
    if (e.key === 'r' || e.key === 'R') {
      if (cloudMode) return  // R key only toggles planet↔read, not cloud
      if (readMode) exitReadMode()
      else enterReadMode()
    }
    if (e.key === 'c' || e.key === 'C') {
      if (cloudMode) exitCloudMode()
      else if (readMode) { exitReadMode(); enterCloudMode() }
      else enterCloudMode()
    }
    if (e.key === 'd' || e.key === 'D') {
      if (cloudMode) exitCloudMode()
      else if (readMode) exitReadMode()
      enterShaderMode()
    }
    updateModeUI()
  })

  animate()
}

// ============================================================
// Background & lights
// ============================================================
// 圆形柔和星点贴图：避免 PointsMaterial 默认的方形像素块
function createStarSprite() {
  const c = document.createElement('canvas')
  c.width = c.height = 64
  const g = c.getContext('2d')
  const grd = g.createRadialGradient(32, 32, 0, 32, 32, 32)
  grd.addColorStop(0, 'rgba(255,255,255,1)')
  grd.addColorStop(0.28, 'rgba(255,255,255,0.78)')
  grd.addColorStop(0.62, 'rgba(255,255,255,0.18)')
  grd.addColorStop(1, 'rgba(255,255,255,0)')
  g.fillStyle = grd
  g.fillRect(0, 0, 64, 64)
  const t = new THREE.CanvasTexture(c)
  t.colorSpace = THREE.SRGBColorSpace
  return t
}

function createBgStars() {
  const geo = new THREE.BufferGeometry()
  const pos = new Float32Array(BG_STAR_COUNT * 3)
  for (let i = 0; i < BG_STAR_COUNT; i++) {
    pos[i * 3] = (Math.random() - 0.5) * 120; pos[i * 3 + 1] = (Math.random() - 0.5) * 70; pos[i * 3 + 2] = (Math.random() - 0.5) * 100 - 20
  }
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  const mat = new THREE.PointsMaterial({ color: 0xccccff, size: 0.09, map: createStarSprite(), alphaTest: 0.02, transparent: true, opacity: 0.8, blending: THREE.AdditiveBlending, depthWrite: false })
  bgStars = new THREE.Points(geo, mat)
  scene.add(bgStars)
}

function setupLights() {
  scene.add(new THREE.AmbientLight(0x112233, 0.5))
  const sun = new THREE.DirectionalLight(0xffeedd, 3.5); sun.position.set(15, 8, 20); scene.add(sun)
  const fill = new THREE.DirectionalLight(0x4466aa, 0.6); fill.position.set(-8, -3, 8); scene.add(fill)
}

// ============================================================
// Animate
// ============================================================
function animate() {
  requestAnimationFrame(animate)

  if (bgStars) { bgStars.rotation.y += 0.00008; bgStars.rotation.x += 0.00004 }
  if (nebulaGroup) nebulaGroup.rotation.y += 0.0003

  // Read mode: skip planet loop, only background + text
  if (readMode) {
    // No planet updates needed; text is rendered by typewriter blocks below
  } else if (allPlanets.length === 0) { if (!cloudMode) renderer.render(scene, camera); return }

  // ---- Planet staging & sweep: 1 hero, 1 hidden backup, others background ----

  // Find the main hero
  let hero = null
  for (const p of allPlanets) {
    if (p.state === 'approach') { hero = p; break }
  }

  // ---- Gap timer: wait 10-20s before next planet enters ----
  if (entranceGap > 0) {
    entranceGap--
    if (entranceGap === 0) {
      // Gap over — stage a fresh planet and launch it SLOWLY
      const p = stagePlanet()
      if (p) {
        p.state = 'approach'
        p.mesh.visible = true
        p.glow.visible = true
        if (p.ring) p.ring.visible = true
      }
    }
  }

  // Only queue a backup while hero is on screen and no gap is running
  if (!stagedBackup && hero && entranceGap === 0) {
    stagedBackup = stagePlanet()
  }

  // Safety: if screen is empty with no gap and we have backup, launch immediately
  if (!hero && entranceGap === 0 && stagedBackup) {
    stagedBackup.state = 'approach'
    stagedBackup.mesh.visible = true
    stagedBackup.glow.visible = true
    if (stagedBackup.ring) stagedBackup.ring.visible = true
    stagedBackup = null
  }

  for (let i = 0; i < allPlanets.length; i++) {
    const p = allPlanets[i]

    // 主角星球换 2K 高清贴图，退回背景立即释放回 1K 缩略图
    if (p.state === 'approach') ensureHeroTexture(p)
    else releaseHeroTexture(p)

    if (p.state === 'orbit') {
      // Background decoration — slow drift in deep space, dim and small
      p.orbitAngle += (0.0015 + Math.random() * 0.0005) * p.orbitDir
      p.mesh.position.x = Math.cos(p.orbitAngle) * p.orbitRadius
      p.mesh.position.y = Math.sin(p.orbitAngle) * p.orbitRadius * 0.45
      p.mesh.position.z = p.orbitZ + Math.sin(Date.now() * 0.0003 + i) * 1.5
      p.mesh.rotation.y += 0.0017
      p.glow.position.copy(p.mesh.position)
      if (p.ring) p.ring.position.copy(p.mesh.position)
    }

    if (p.state === 'staging') {
      // Hidden backup — off-screen, invisible. Nothing to do.
    }

    if (p.state === 'approach') {
      // The ONE hero planet
      const traj = p._traj
      const axis = p._axis

      // Progress based on position
      let progress
      if (axis === 'x') {
        progress = (p.mesh.position.x - p._ex) / (p._xx - p._ex)
      } else if (axis === 'y') {
        progress = (p.mesh.position.y - p._ey) / (p._xy - p._ey)
      } else {
        // Diagonal: use combined progress
        const dx = (p.mesh.position.x - p._ex) / (p._xx - p._ex)
        const dy = (p.mesh.position.y - p._ey) / (p._xy - p._ey)
        progress = Math.max(dx, dy)
      }
      progress = Math.max(0, Math.min(1, progress))

      // Speed: sine easing — extremely slow edge entry, modest mid-sweep (5x slower)
      const base  = 0.0016
      const ampl  = 0.0014
      const speed = base + Math.sin(progress * Math.PI) * ampl

      // Move
      if (axis === 'x') {
        p.mesh.position.x += speed * p._dir
        p.mesh.position.y += (p._ey - p.mesh.position.y) * 0.015
      } else if (axis === 'y') {
        p.mesh.position.y += speed * p._dir
        p.mesh.position.x += (p._ex - p.mesh.position.x) * 0.015
      } else {
        p.mesh.position.x += speed * p._dir
        p.mesh.position.y += speed * (p._dy || 0.7)
      }

      // z: asymptotic drift toward MAIN_Z — fastest when far, slows as it approaches — zero jerk
      p.mesh.position.z += (MAIN_Z - p.mesh.position.z) * 0.004
      // Scale tracks z directly, clamped to random max 5-8x
      const sc = Math.min(sweepScale(p.mesh.position.z, p), p._maxScale || 12)
      p.mesh.scale.setScalar(sc)
      p.glow.scale.setScalar(sc * 1.2)
      if (p.ring) p.ring.scale.setScalar(sc)
      p.glow.position.copy(p.mesh.position)
      if (p.ring) p.ring.position.copy(p.mesh.position)
      // 自转速度与视在尺寸成反比：特写时接近静止，远景时保持缓慢自转
      // 屏幕上的像素流速 ∝ 角速度 × 半径，真实贴图细节多，大尺寸高速旋转会晕
      // 放宽约一倍：远景约 7.6°/秒，特写约 3°/秒，仍随放大递减
      const spin = Math.min(0.0022, Math.max(0.00022, 0.0025 / Math.max(sc, 0.2)))
      p.mesh.rotation.y += spin
      if (p.ring && !p.ring.userData.texturedRing) p.ring.rotation.z += 0.002

      // Exit check
      let past = false
      if (axis === 'x') past = p._dir > 0 ? p.mesh.position.x > EXIT_PAST : p.mesh.position.x < -EXIT_PAST
      else if (axis === 'y') past = p._dir > 0 ? p.mesh.position.y > EXIT_PAST : p.mesh.position.y < -EXIT_PAST
      else past = Math.abs(p.mesh.position.x) > EXIT_PAST || Math.abs(p.mesh.position.y) > EXIT_PAST - 2

      if (past) {
        // Planet naturally off-screen — return to background orbit
        p.state = 'orbit'
        p.mesh.position.set(p.orbitX, p.orbitY, p.orbitZ)
        p.mesh.scale.setScalar(p.originScale)
        p.glow.position.copy(p.mesh.position)
        p.glow.scale.setScalar(p.originScale * 1.2)
        if (p.ring) { p.ring.position.copy(p.mesh.position); p.ring.scale.setScalar(p.originScale) }
        // Discard old backup, start gap before next planet
        stagedBackup = null
        entranceGapTarget = 600 + Math.floor(Math.random() * 600)  // 10-20s
        entranceGap = entranceGapTarget
      }
    }
  }

  // ---- Typewriter ----
  // 3D 模式：翻页由环形动画驱动、文字整卡显示，跳过打字机与自动翻页
  if (shaderMode) return

  // 暂停：文字推进与自动翻页冻结，背景与画面照常绘制
  if (paused) {
    renderText()
    if (!cloudMode) renderer.render(scene, camera)
    return
  }

  const totalTarget = typingPhase === 'front' ? frontCharCount
    : (typingPhase === 'pause' || typingPhase === 'back') ? frontCharCount + backCharCount
    : 0

  if (typingPhase === 'front' && revealedChars < frontCharCount) {
    if (!readModePaused) {
    revealTimer++
    if (revealTimer >= revealSpeed / 16) {
      revealedChars++
      revealTimer = 0
      revealSpeed = 45 + Math.random() * 50
      renderText()
    }
    }
  }

  if (typingPhase === 'front' && revealedChars >= frontCharCount) {
    typingPhase = 'pause'
    pauseTimer = 0
  }

  if (typingPhase === 'pause') {
    if (!readModePaused) {
    pauseTimer++
    }
    if (pauseTimer > THINK_PAUSE) {
      typingPhase = 'back'
      revealSpeed = 45
      revealTimer = 0
    }
    renderText()  // keep cursor blinking
  }

  if (typingPhase === 'back' && revealedChars < frontCharCount + backCharCount) {
    if (!readModePaused) {
    revealTimer++
    if (revealTimer >= revealSpeed / 16) {
      revealedChars++
      revealTimer = 0
      revealSpeed = 45 + Math.random() * 50
      renderText()
    }
    }
  }

  if (typingPhase === 'back' && revealedChars >= frontCharCount + backCharCount) {
    typingPhase = 'done'
    // Delay based on answer length — works in both modes
    readModeDelay = Math.max(2000, backCharCount * 80)  // min 2s, +80ms per answer char
    readModeTimer = 0
    renderText()
  }

  // Auto-advance: wait out delay, then next card (starfield + read mode both)
  if (typingPhase === 'done') {
    if (readModePaused) {
      renderText()
    } else {
      readModeTimer++
      renderText()
      if (readModeTimer >= readModeDelay) {
        currentCardIdx++
        startCard()
      }
    }
  }

  // 3D 模式由独立 WebGL 循环绘制，暂停 Three.js 渲染
  if (!shaderMode && !cloudMode) renderer.render(scene, camera)
}

init()
