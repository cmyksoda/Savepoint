import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';

const platformNames = new Map([
  [6, 'PC'], [3, 'PC'], [14, 'PC'], [13, 'PC'], [18, 'NES'], [58, 'SNES'], [4, 'Nintendo 64'], [21, 'GameCube'],
  [5, 'Wii'], [41, 'Wii U'], [130, 'Nintendo Switch'], [33, 'Game Boy'],
  [22, 'Game Boy Color'], [24, 'Game Boy Advance'], [20, 'Nintendo DS'],
  [159, 'Nintendo DSi'], [37, 'Nintendo 3DS'], [87, 'Virtual Boy'],
  [51, 'Famicom Disk System'], [166, 'Pokémon mini'],
]);

export function normalize(games) {
  return games.flatMap(game => (game.platforms ?? []).map(platform => {
    const system = platformNames.get(platform.id)
      ?? (platform.name?.includes('Switch 2') ? 'Nintendo Switch 2' : platform.name);
    if (!system) return null;
    const dates = (game.release_dates ?? []).filter(date => date.platform === platform.id && date.date);
    const release = dates.length ? Math.min(...dates.map(date => date.date)) * 1000 : null;
    return {
      title: game.name, system, release, source: 'igdb', sourceId: String(game.id),
      cover: game.cover?.image_id
        ? `https://images.igdb.com/igdb/image/upload/t_cover_big/${game.cover.image_id}.jpg` : '',
    };
  }).filter(Boolean));
}

let token;
let tokenExpires = 0;
async function credentials() {
  if (!process.env.TWITCH_CLIENT_ID || !process.env.TWITCH_CLIENT_SECRET) {
    throw new Error('Catalog credentials are not configured');
  }
  if (token && Date.now() < tokenExpires) return token;
  const params = new URLSearchParams({
    client_id: process.env.TWITCH_CLIENT_ID,
    client_secret: process.env.TWITCH_CLIENT_SECRET,
    grant_type: 'client_credentials',
  });
  const response = await fetch('https://id.twitch.tv/oauth2/token', {
    method: 'POST', body: params, signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) throw new Error('Catalog authentication unavailable');
  const data = await response.json();
  token = data.access_token;
  tokenExpires = Date.now() + Math.max(0, data.expires_in - 60) * 1000;
  return token;
}

const cache = new Map();
let requests = [];

export const server = createServer(async (request, response) => {
  response.setHeader('Content-Type', 'application/json; charset=utf-8');
  response.setHeader('Cache-Control', 'no-store');
  const send = (status, body) => { response.writeHead(status); response.end(JSON.stringify(body)); };
  const url = new URL(request.url, 'http://localhost');
  if (request.method !== 'GET') return send(405, { error: 'GET required' });
  if (url.pathname === '/health') return send(200, { status: 'ok' });
  if (url.pathname !== '/search') return send(404, { error: 'Not found' });
  const query = url.searchParams.get('q')?.trim();
  if (!query || query.length > 120) return send(400, { error: 'Enter a title of 1–120 characters' });
  const cached = cache.get(query.toLowerCase());
  if (cached && cached.expires > Date.now()) return send(200, cached.body);
  requests = requests.filter(time => time > Date.now() - 1000);
  if (requests.length >= 3) return send(429, { error: 'Try again shortly' });
  requests.push(Date.now());
  try {
    const accessToken = await credentials();
    const safeQuery = query.replace(/[\\"\r\n]/g, ' ');
    const upstream = await fetch('https://api.igdb.com/v4/games', {
      method: 'POST',
      headers: { 'Client-ID': process.env.TWITCH_CLIENT_ID, Authorization: `Bearer ${accessToken}` },
      body: `search "${safeQuery}"; fields name,cover.image_id,platforms.name,release_dates.date,release_dates.platform; limit 30;`,
      signal: AbortSignal.timeout(15000),
    });
    if (upstream.status === 401) tokenExpires = 0;
    if (!upstream.ok) throw new Error('Catalog search unavailable');
    const body = { results: normalize(await upstream.json()), attribution: 'Game data provided by IGDB' };
    for (const [key, entry] of cache) if (entry.expires <= Date.now()) cache.delete(key);
    cache.set(query.toLowerCase(), { expires: Date.now() + 300000, body });
    send(200, body);
  } catch {
    send(503, { error: 'Catalog search unavailable. Manual entry is still available.' });
  }
});

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  server.listen(Number(process.env.PORT ?? 8787), process.env.HOST ?? '127.0.0.1', () => {
    console.log('Savepoint catalog service ready');
  });
}
