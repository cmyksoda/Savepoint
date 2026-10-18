import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalize, server, catalogSearchQuery, parseFamilies } from './server.mjs';

test('release dates follow the chosen system rather than the earliest unrelated release', () => {
  const results = normalize([{
    id: 42, name: 'A game', cover: { image_id: 'cover42' },
    platforms: [{ id: 6, name: 'PC' }, { id: 130, name: 'Nintendo Switch' }],
    release_dates: [{ platform: 6, date: 100 }, { platform: 130, date: 500 }],
  }]);
  assert.equal(results[0].release, 100000);
  assert.equal(results[1].release, 500000);
  assert.match(results[0].cover, /^https:\/\/images\.igdb\.com\//);
});

test('missing dates remain unknown and newer platform names survive', () => {
  const results = normalize([{ id: 1, name: 'New game', platforms: [{ id: 99999, name: 'Nintendo Switch 2' }] }]);
  assert.equal(results[0].system, 'Nintendo Switch 2');
  assert.equal(results[0].release, null);
  assert.equal(results[0].cover, '');
});

test('service validates input and does not expose credential errors', async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const base = `http://127.0.0.1:${server.address().port}`;
    assert.equal((await fetch(`${base}/search`)).status, 400);
    assert.equal((await fetch(`${base}/unknown`)).status, 404);
    const unavailable = await fetch(`${base}/search?q=metroid`);
    assert.equal(unavailable.status, 503);
    assert.doesNotMatch(await unavailable.text(), /secret|access_token/i);
  } finally { await new Promise(resolve => server.close(resolve)); }
});

test('Virtual Console releases are excluded while original Nintendo hardware remains', () => {
  const results = normalize([{ id: 10, name: 'Metroid: Zero Mission', platforms: [{ id: 24 }, { id: 41 }, { id: 130 }], release_dates: [{ platform: 24, date: 100 }, { platform: 41, date: 1000 }, { platform: 130, date: 2000 }] }]);
  assert.deepEqual(results.map(game => game.system), ['Game Boy Advance']);
});

test('Persona on Switch and enhanced Nintendo editions remain available', () => {
  const persona = normalize([{ id: 11, name: 'Persona 5 Royal', platforms: [{ id: 48, name: 'PlayStation 4' }, { id: 130 }], release_dates: [{ platform: 48, date: 100 }, { platform: 130, date: 1000 }] }]);
  assert.equal(persona[0].system, 'Nintendo Switch');
  const deluxe = normalize([{ id: 12, name: 'A game Deluxe', platforms: [{ id: 18 }, { id: 130 }], release_dates: [{ platform: 18, date: 100 }, { platform: 130, date: 1000 }] }]);
  assert.deepEqual(deluxe.map(game => game.system), ['NES', 'Nintendo Switch']);
});

test('modern native ports and remakes are not treated as Virtual Console', () => {
  const results = normalize([{ id: 13, name: 'A modern game', platforms: [{ id: 41 }, { id: 130 }], release_dates: [{ platform: 41, date: 100 }, { platform: 130, date: 1000 }] }]);
  assert.deepEqual(results.map(game => game.system), ['Wii U', 'Nintendo Switch']);
});

test('live-service updates do not crowd out a base game or a real remake', () => {
  const results = normalize([
    { id: 119277, name: 'Genshin Impact', game_type: 0, platforms: [{ id: 6 }] },
    { id: 288670, name: 'Genshin Impact: An update', game_type: 14, parent_game: 119277, platforms: [{ id: 6 }] },
    { id: 3, name: 'A remake', game_type: 8, platforms: [{ id: 130 }] },
  ]);
  assert.deepEqual(results.map(game => game.title), ['Genshin Impact', 'A remake']);
});

test('search pages exclude updates and exact matches use a separate lookup', () => {
  assert.match(catalogSearchQuery('genshin impact', 50), /search "genshin impact"; where game_type != 14;/);
  assert.match(catalogSearchQuery('genshin impact', 50), /offset 50;/);
  assert.match(catalogSearchQuery('genshin impact', 0, true), /where name ~ "genshin impact" & game_type != 14;/);
  assert.doesNotMatch(catalogSearchQuery('bad"\nquery'), /bad"\nquery/);
});

test('other consoles appear only for the families an app asks for', () => {
  const persona = [{ id: 11, name: 'Persona 5 Royal', platforms: [{ id: 48, name: 'PlayStation 4' }, { id: 130 }],
    release_dates: [{ platform: 48, date: 100 }, { platform: 130, date: 1000 }] }];
  assert.deepEqual(normalize(persona).map(game => game.system), ['Nintendo Switch']);
  assert.deepEqual(normalize(persona, parseFamilies('playstation,nintendo')).map(game => game.system), ['PlayStation 4', 'Nintendo Switch']);
  assert.deepEqual(normalize(persona, parseFamilies('playstation')).map(game => game.system), ['PlayStation 4']);
  assert.deepEqual([...parseFamilies('unknown,')], ['nintendo', 'pc']);
  assert.deepEqual([...parseFamilies(null)], ['nintendo', 'pc']);
});

test('results carry how many people rated each game, so apps can put well-known games first', () => {
  const results = normalize([{ id: 1, name: 'Super Metroid', total_rating_count: 812, platforms: [{ id: 58 }] },
    { id: 2, name: 'Metroid Fool', platforms: [{ id: 21 }] }]);
  assert.deepEqual(results.map(game => game.popularity), [812, 0]);
});
