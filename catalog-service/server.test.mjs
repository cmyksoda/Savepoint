import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalize, server } from './server.mjs';

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
