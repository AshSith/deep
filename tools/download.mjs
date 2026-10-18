import fs from 'node:fs'
import path from 'node:path'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'

const url = process.argv[2]
const dest = process.argv[3]
if (!url || !dest) {
  console.error('usage: node download.mjs <url> <dest>')
  process.exit(1)
}

fs.mkdirSync(path.dirname(dest), { recursive: true })
if (fs.existsSync(dest) && fs.statSync(dest).size > 1024) {
  console.log('skip existing', dest, fs.statSync(dest).size)
  process.exit(0)
}

const res = await fetch(url, { redirect: 'follow' })
if (!res.ok || !res.body) {
  console.error('HTTP', res.status, await res.text().catch(() => ''))
  process.exit(1)
}

const total = Number(res.headers.get('content-length') || 0)
let seen = 0
let last = 0
const source = Readable.fromWeb(res.body)
source.on('data', (chunk) => {
  seen += chunk.length
  const now = Date.now()
  if (now - last > 3000) {
    last = now
    console.log(`${path.basename(dest)} ${(seen / 1e6).toFixed(1)}MB${total ? ` / ${(total / 1e6).toFixed(1)}MB` : ''}`)
  }
})

await pipeline(source, fs.createWriteStream(dest))
console.log('saved', dest, fs.statSync(dest).size)
