import fs from 'node:fs'
import path from 'node:path'

const url = process.argv[2]
const dest = process.argv[3]
const parts = Number(process.argv[4] || 8)

if (!url || !dest) {
  console.error('usage: node download_parallel.mjs <url> <dest> [parts]')
  process.exit(1)
}

fs.mkdirSync(path.dirname(dest), { recursive: true })

const probe = await fetch(url, { headers: { Range: 'bytes=0-0' }, redirect: 'follow' })
const range = probe.headers.get('content-range') || ''
const total = Number(range.split('/')[1] || probe.headers.get('content-length') || 0)
if (!total) {
  console.error('cannot determine size', probe.status, range)
  process.exit(1)
}
console.log('total', (total / 1e6).toFixed(1), 'MB, parts', parts)

const chunk = Math.ceil(total / parts)
const started = Date.now()

async function downloadPart(i) {
  const start = i * chunk
  const end = Math.min(total - 1, (i + 1) * chunk - 1)
  const file = `${dest}.part${i}`
  if (fs.existsSync(file) && fs.statSync(file).size === end - start + 1) {
    console.log(`part ${i} exists`)
    return
  }
  let lastError = null
  for (let attempt = 1; attempt <= 5; attempt += 1) {
    try {
      const res = await fetch(url, {
        headers: { Range: `bytes=${start}-${end}` },
        redirect: 'follow',
      })
      if (res.status !== 206) throw new Error(`status ${res.status}`)
      const buffer = Buffer.from(await res.arrayBuffer())
      if (buffer.length !== end - start + 1) {
        throw new Error(`short read ${buffer.length}/${end - start + 1}`)
      }
      fs.writeFileSync(file, buffer)
      console.log(`part ${i} done ${(buffer.length / 1e6).toFixed(1)}MB in ${((Date.now() - started) / 1000).toFixed(0)}s`)
      return
    } catch (error) {
      lastError = error
      console.log(`part ${i} attempt ${attempt} failed: ${error.message}`)
      await new Promise((resolve) => setTimeout(resolve, 1500 * attempt))
    }
  }
  throw lastError
}

await Promise.all(Array.from({ length: parts }, (_, i) => downloadPart(i)))

const out = fs.createWriteStream(dest)
for (let i = 0; i < parts; i += 1) {
  const file = `${dest}.part${i}`
  await new Promise((resolve, reject) => {
    const input = fs.createReadStream(file)
    input.pipe(out, { end: false })
    input.on('end', resolve)
    input.on('error', reject)
  })
}
await new Promise((resolve) => out.end(resolve))
for (let i = 0; i < parts; i += 1) fs.unlinkSync(`${dest}.part${i}`)
console.log('saved', dest, fs.statSync(dest).size)
