const root = 'https://gamesdb.launchbox-app.com';

const platformNames = new Map([
  ['Nintendo Entertainment System', 'NES'], ['Nintendo Famicom Disk System', 'Famicom Disk System'],
  ['Super Nintendo Entertainment System', 'SNES'], ['Nintendo 64', 'Nintendo 64'],
  ['Nintendo GameCube', 'GameCube'], ['Nintendo Wii', 'Wii'], ['Nintendo Wii U', 'Wii U'],
  ['Nintendo Switch', 'Nintendo Switch'], ['Nintendo Switch 2', 'Nintendo Switch 2'],
  ['Nintendo Game Boy', 'Game Boy'], ['Nintendo Game Boy Color', 'Game Boy Color'],
  ['Nintendo Game Boy Advance', 'Game Boy Advance'], ['Nintendo DS', 'Nintendo DS'],
  ['Nintendo DSi', 'Nintendo DSi'], ['Nintendo 3DS', 'Nintendo 3DS'], ['Nintendo Virtual Boy', 'Virtual Boy'],
  ['Nintendo Pokemon Mini', 'Pokémon mini'], ['Windows', 'PC'], ['Linux', 'PC'], ['Mac OS', 'PC'],
  ['Sony Playstation', 'PlayStation'], ['Sony Playstation 2', 'PlayStation 2'], ['Sony Playstation 3', 'PlayStation 3'],
  ['Sony Playstation 4', 'PlayStation 4'], ['Sony Playstation 5', 'PlayStation 5'], ['Sony PSP', 'PSP'],
  ['Sony Playstation Vita', 'PlayStation Vita'], ['Microsoft Xbox', 'Xbox'], ['Microsoft Xbox 360', 'Xbox 360'],
  ['Microsoft Xbox One', 'Xbox One'], ['Microsoft Xbox Series X/S', 'Xbox Series X|S'],
  ['Sega Master System', 'Master System'], ['Sega Genesis', 'Genesis'], ['Sega CD', 'Sega CD'],
  ['Sega Saturn', 'Sega Saturn'], ['Sega Dreamcast', 'Dreamcast'], ['Sega Game Gear', 'Game Gear'],
  ['Apple iOS', 'iOS'], ['Android', 'Android'], ['Atari 2600', 'Atari 2600'], ['Atari 7800', 'Atari 7800'],
  ['Atari Lynx', 'Atari Lynx'], ['NEC TurboGrafx-16', 'TurboGrafx-16'], ['SNK Neo Geo AES', 'Neo Geo'],
  ['SNK Neo Geo Pocket Color', 'Neo Geo Pocket Color'], ['Bandai WonderSwan', 'WonderSwan'], ['Arcade', 'Arcade'],
]);

function text(html) {
  return html.replace(/<[^>]*>/g, '').replace(/&#(x[\da-f]+|\d+);/gi, (_, n) => String.fromCodePoint(n[0].toLowerCase() === 'x' ? parseInt(n.slice(1), 16) : Number(n)))
    .replace(/&(amp|quot|apos|lt|gt|nbsp);/g, (_, n) => ({ amp: '&', quot: '"', apos: "'", lt: '<', gt: '>', nbsp: ' ' }[n])).trim();
}

export function parseLaunchBoxGames(html) {
  const games = [];
  for (const match of html.matchAll(/<a\s[^>]*href="(\/games\/details\/(\d+)-[^"<>]+)"[^>]*>([\s\S]*?)<\/a>/g)) {
    const title = match[3].match(/<h3[^>]*>([\s\S]*?)<\/h3>/);
    const platform = match[3].match(/<h3[^>]*>[\s\S]*?<\/h3>\s*<p[^>]*>([\s\S]*?)<\/p>/);
    if (title && platform) games.push({ title: text(title[1]), system: platformNames.get(text(platform[1])), path: match[1], id: match[2] });
  }
  return games;
}

export function parseLaunchBoxCovers(html, game) {
  const covers = [];
  for (const match of html.matchAll(/<a\s[^>]*href="(https:\/\/images\.launchbox-app\.com\/[^"<>]+)"[^>]*data-title="([^"]+)"[^>]*>/g)) {
    const label = text(match[2]);
    if (!label.includes(' - Box - Front Image') || label.includes('Fanart')) continue;
    const uri = new URL(match[1]);
    if (uri.protocol !== 'https:' || uri.hostname !== 'images.launchbox-app.com') continue;
    covers.push({ url: uri.href, provider: 'LaunchBox', title: game.title, system: game.system, label, sourceId: game.id, attribution: 'Cover via LaunchBox Games Database' });
  }
  return covers.sort((a, b) => Number(!a.label.includes('North America')) - Number(!b.label.includes('North America')));
}

const canonical = title => title.normalize('NFKD').replace(/\p{Diacritic}/gu, '').toLowerCase().replace(/[^a-z0-9]/g, '');

export async function launchBoxCovers(title, system) {
  const get = async path => {
    let url = new URL(path, root);
    for (let redirects = 0; redirects <= 5; redirects++) {
      const response = await fetch(url, { headers: { 'User-Agent': 'Savepoint/0.3 (personal game library)' }, signal: AbortSignal.timeout(15000), redirect: 'manual' });
      if ([301, 302, 303, 307, 308].includes(response.status)) {
        const location = response.headers.get('location');
        if (!location) throw new Error('LaunchBox redirect unavailable');
        url = new URL(location, url);
        if (url.origin !== root) throw new Error('Unexpected LaunchBox redirect');
        continue;
      }
      if (!response.ok) throw new Error('LaunchBox unavailable');
      return response.text();
    }
    throw new Error('Too many LaunchBox redirects');
  };
  const games = parseLaunchBoxGames(await get('/games/results/' + encodeURIComponent(title)));
  const match = games.find(game => game.system === system && (canonical(game.title) === canonical(title) || (canonical(title) === 'am2r' && canonical(game.title).startsWith('am2r'))));
  if (!match) return [];
  return parseLaunchBoxCovers(await get(match.path.replace('/details/', '/images/')), match);
}
