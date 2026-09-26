import test from 'node:test'
import assert from 'node:assert/strict'
import { campusPlaces, campusMap, searchCampusPlaces } from '../src/config/campusPlaces.ts'

test('campus aliases resolve to their target before broader matches', () => {
  assert.equal(searchCampusPlaces(' 一教 ')[0].id, 'teaching-1')
  assert.equal(searchCampusPlaces('南门')[0].id, 'south-gate')
  assert.equal(searchCampusPlaces('图书馆')[0].id, 'library')
  assert.equal(searchCampusPlaces('教学楼').length, 2)
  assert.deepEqual(searchCampusPlaces('不存在的地点'), [])
})

test('searchable places have distinct valid positions on the bundled map', () => {
  assert.equal(new Set(campusPlaces.map((p) => p.id)).size, campusPlaces.length)
  for (const place of campusPlaces) {
    assert.ok(place.x >= 0 && place.x <= campusMap.width)
    assert.ok(place.y >= 0 && place.y <= campusMap.height)
  }
})
