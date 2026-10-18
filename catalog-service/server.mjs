import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { launchBoxCovers } from './covers.mjs';

const platformNames = new Map([
  [6, 'PC'], [3, 'PC'], [14, 'PC'], [13, 'PC'], [18, 'NES'], [58, 'SNES'], [4, 'Nintendo 64'], [21, 'GameCube'],
  [5, 'Wii'], [41, 'Wii U'], [130, 'Nintendo Switch'], [33, 'Game Boy'],
  [22, 'Game Boy Color'], [24, 'Game Boy Advance'], [20, 'Nintendo DS'],
  [159, 'Nintendo DSi'], [37, 'Nintendo 3DS'], [87, 'Virtual Boy'],
  [51, 'Famicom Disk System'], [166, 'Pokémon mini'],
]);

// Other consoles are matched by IGDB's platform names; older apps never ask for them.
const namedSystems = new Map([
  ['PlayStation', 'PlayStation'], ['PlayStation 2', 'PlayStation 2'], ['PlayStation 3', 'PlayStation 3'],
  ['PlayStation 4', 'PlayStation 4'], ['PlayStation 5', 'PlayStation 5'], ['PlayStation Portable', 'PSP'],
  ['PlayStation Vita', 'PlayStation Vita'], ['Xbox', 'Xbox'], ['Xbox 360', 'Xbox 360'], ['Xbox One', 'Xbox One'],
  ['Xbox Series X|S', 'Xbox Series X|S'], ['Sega Master System/Mark III', 'Master System'],
  ['Sega Mega Drive/Genesis', 'Genesis'], ['Sega CD', 'Sega CD'], ['Sega Saturn', 'Sega Saturn'],
  ['Dreamcast', 'Dreamcast'], ['Sega Game Gear', 'Game Gear'], ['iOS', 'iOS'], ['Android', 'Android'],
  ['Atari 2600', 'Atari 2600'], ['Atari 7800', 'Atari 7800'], ['Atari Lynx', 'Atari Lynx'],
  ['TurboGrafx-16/PC Engine', 'TurboGrafx-16'], ['Neo Geo AES', 'Neo Geo'], ['Neo Geo MVS', 'Neo Geo'],
  ['Neo Geo Pocket Color', 'Neo Geo Pocket Color'], ['WonderSwan', 'WonderSwan'],
  ['WonderSwan Color', 'WonderSwan'], ['Arcade', 'Arcade'],
]);

const familyOf = new Map([
  ...['PC'].map(system => [system, 'pc']),
  ...['NES', 'Famicom Disk System', 'SNES', 'Nintendo 64', 'GameCube', 'Wii', 'Wii U', 'Nintendo Switch',
    'Nintendo Switch 2', 'Game Boy', 'Game Boy Color', 'Game Boy Advance', 'Nintendo DS', 'Nintendo DSi',
    'Nintendo 3DS', 'Virtual Boy', 'Pokémon mini'].map(system => [system, 'nintendo']),
  ...['PlayStation', 'PlayStation 2', 'PlayStation 3', 'PlayStation 4', 'PlayStation 5', 'PSP',
    'PlayStation Vita'].map(system => [system, 'playstation']),
  ...['Xbox', 'Xbox 360', 'Xbox One', 'Xbox Series X|S'].map(system => [system, 'xbox']),
  ...['Master System', 'Genesis', 'Sega CD', 'Sega Saturn', 'Dreamcast', 'Game Gear'].map(system => [system, 'sega']),
  ...['iOS', 'Android'].map(system => [system, 'mobile']),
  ...['Atari 2600', 'Atari 7800', 'Atari Lynx', 'TurboGrafx-16', 'Neo Geo', 'Neo Geo Pocket Color', 'WonderSwan',
    'Arcade'].map(system => [system, 'retro']),
]);

export const defaultFamilies = new Set(['nintendo', 'pc']);
const allFamilies = new Set(familyOf.values());

export function parseFamilies(value) {
  if (!value) return defaultFamilies;
  const chosen = new Set(value.split(',').map(family => family.trim()).filter(family => allFamilies.has(family)));
  return chosen.size ? chosen : defaultFamilies;
}

const searchPageSize = 50;
const gameFields = 'name,game_type,total_rating_count,cover.image_id,platforms.name,release_dates.date,release_dates.platform';

export function catalogSearchQuery(query, offset = 0, exact = false) {
  const safeQuery = query.replace(/[\\"\r\n]/g, ' ');
  const match = exact ? `where name ~ "${safeQuery}" & game_type != 14;` : `search "${safeQuery}"; where game_type != 14;`;
  return `${match} fields ${gameFields}; limit ${searchPageSize}; offset ${offset};`;
}

export function normalize(games, families = defaultFamilies) {
  const results = [];
  const retroNintendo = new Set([18, 51, 58, 4, 33, 22, 24, 20, 21]);
  const virtualConsoleHosts = new Set([5, 41, 37, 130]);
  for (const game of games) {
    if (game.game_type === 14) continue;
    const dated = (game.release_dates ?? []).filter(date => Number.isFinite(date.date) && date.platform);
    const earliest = dated.length ? Math.min(...dated.map(date => date.date)) : null;
    const originalIds = new Set(dated.filter(date => date.date === earliest).map(date => date.platform));
    const retroOriginal = [...originalIds].some(id => retroNintendo.has(id));
    const enhanced = /\b(deluxe|remake|remaster(?:ed)?|definitive|hd)\b/i.test(game.name);
    const bySystem = new Map();
    for (const platform of game.platforms ?? []) {
      const system = platformNames.get(platform.id) ??
        (platform.name?.includes('Switch 2') ? 'Nintendo Switch 2' : namedSystems.get(platform.name));
      if (!system || !families.has(familyOf.get(system))) continue;
      const dates = dated.filter(date => date.platform === platform.id);
      const release = dates.length ? Math.min(...dates.map(date => date.date)) : null;
      const virtualConsole = retroOriginal && !enhanced && !originalIds.has(platform.id) &&
        (virtualConsoleHosts.has(platform.id) || system === 'Nintendo Switch 2') && release !== null && release > earliest;
      if (virtualConsole) continue;
      const item = { title: game.name, system, release: release === null ? null : release * 1000, source: 'igdb', sourceId: String(game.id),
        popularity: game.total_rating_count ?? 0,
        cover: game.cover?.image_id ? `https://images.igdb.com/igdb/image/upload/t_cover_big/${game.cover.image_id}.jpg` : '' };
      const previous = bySystem.get(system);
      if (!previous || (item.release !== null && (previous.release === null || item.release < previous.release))) bySystem.set(system, item);
    }
    results.push(...bySystem.values());
  }
  return results;
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
  if (!['/search', '/covers'].includes(url.pathname)) return send(404, { error: 'Not found' });
  const query = url.searchParams.get('q')?.trim();
  if (!query || query.length > 120) return send(400, { error: 'Enter a title of 1–120 characters' });
  const enteredOffset = url.searchParams.get('offset') ?? '0';
  const offset = Number(enteredOffset);
  if (!/^\d+$/.test(enteredOffset) || !Number.isSafeInteger(offset)) return send(400, { error: 'Invalid result page' });
  const provider = url.pathname === '/covers' ? url.searchParams.get('provider') : null;
  const system = url.searchParams.get('system')?.trim() ?? '';
  const families = parseFamilies(url.searchParams.get('families'));
  if (provider && !['LaunchBox', 'IGDB'].includes(provider)) return send(400, { error: 'Unknown cover provider' });
  const key = `${url.pathname}:${provider}:${system}:${[...families].sort()}:${query.toLowerCase()}:${offset}`;
  const cached = cache.get(key);
  if (cached && cached.expires > Date.now()) return send(200, cached.body);
  requests = requests.filter(time => time > Date.now() - 1000);
  if (requests.length >= 3) return send(429, { error: 'Try again shortly' });
  requests.push(Date.now());
  try {
    if (provider === 'LaunchBox') {
      const body = { results: await launchBoxCovers(query, system) };
      cache.set(key, { expires: Date.now() + 300000, body });
      return send(200, body);
    }
    const accessToken = await credentials();
    async function igdbGames(exact = false) {
      const upstream = await fetch('https://api.igdb.com/v4/games', {
        method: 'POST',
        headers: { 'Client-ID': process.env.TWITCH_CLIENT_ID, Authorization: `Bearer ${accessToken}` },
        body: catalogSearchQuery(query, exact ? 0 : offset, exact),
        signal: AbortSignal.timeout(15000),
      });
      if (upstream.status === 401) tokenExpires = 0;
      if (!upstream.ok) throw new Error('Catalog search unavailable');
      return upstream.json();
    }
    const [page, exact] = await Promise.all([igdbGames(), offset === 0 ? igdbGames(true) : []]);
    const entries = new Map([...exact, ...page].map(game => [game.id, game]));
    const games = normalize([...entries.values()], provider === 'IGDB' ? allFamilies : families);
    const body = {
      results: provider === 'IGDB' ? games.filter(game => game.system === system && game.cover).map(game => ({ url: game.cover, title: game.title, system: game.system, label: game.title, provider: 'IGDB', sourceId: game.sourceId, attribution: 'Cover via IGDB' })) : games,
      nextOffset: page.length === searchPageSize ? offset + searchPageSize : null,
      attribution: 'Game data provided by IGDB',
    };
    for (const [key, entry] of cache) if (entry.expires <= Date.now()) cache.delete(key);
    cache.set(key, { expires: Date.now() + 300000, body });
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
