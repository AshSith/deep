import fs from 'node:fs'

const xml = await (await fetch('https://dl.google.com/android/repository/repository2-1.xml')).text()
const wanted = new Set(['platform-tools', 'platforms;android-35', 'build-tools;35.0.0'])
const packages = []

for (const match of xml.matchAll(/<remotePackage path="([^"]+)">([\s\S]*?)<\/remotePackage>/g)) {
  const path = match[1]
  if (!wanted.has(path)) continue
  const body = match[2]
  const archives = [...body.matchAll(/<archive>([\s\S]*?)<\/archive>/g)].map((a) => a[1])
  const urls = []
  for (const archive of archives) {
    const url = /<url>([^<]+)<\/url>/.exec(archive)?.[1]
    const hostOs = /<host-os>([^<]+)<\/host-os>/.exec(archive)?.[1]
    if (url && (!hostOs || hostOs === 'windows')) urls.push({ url, hostOs: hostOs || 'any' })
  }
  packages.push({ path, urls })
}

console.log(JSON.stringify(packages, null, 2))
fs.writeFileSync('sdk-packages.json', JSON.stringify(packages, null, 2))
