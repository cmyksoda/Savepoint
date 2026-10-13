import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseLaunchBoxGames, parseLaunchBoxCovers } from './covers.mjs';

test('LaunchBox preserves title entities and matches original handheld platform', () => {
  const games = parseLaunchBoxGames('<a href="/games/details/3552-metroid-zero-mission"><h3>Metroid: Zero Mission</h3><p>Nintendo Game Boy Advance</p></a><a href="/games/details/1-other"><h3>A &amp; B</h3><p>Windows</p></a>');
  assert.equal(games[0].system, 'Game Boy Advance');
  assert.equal(games[0].id, '3552');
  assert.equal(games[1].title, 'A & B');
});

test('LaunchBox uses official front covers, puts North America first, and excludes fanart and back covers', () => {
  const link = (name, label) => `<a href="https://images.launchbox-app.com/${name}.jpg" data-title="${label}">`;
  const html = link('eu', 'Europe - Box - Front Image') + link('back', 'North America - Box - Back Image') + link('fan', 'North America - Fanart - Box - Front Image') + link('us', 'North America - Box - Front Image');
  const covers = parseLaunchBoxCovers(html, {title:'Metroid',system:'Game Boy Advance',id:'3552'});
  assert.deepEqual(covers.map(cover => cover.url.split('/').pop()), ['us.jpg','eu.jpg']);
  assert.ok(covers.every(cover => cover.provider === 'LaunchBox' && cover.sourceId === '3552'));
});
