import test from 'node:test'
import assert from 'node:assert/strict'
import { parsePriceQuery, validatePriceRange } from '../src/utils/marketFilters.ts'

test('URL prices preserve zero and decimals, reject invalid numbers', () => {
  assert.equal(parsePriceQuery('0'), '0')
  assert.equal(parsePriceQuery(['12.50', '90']), '12.5')
  for (const input of ['-1', 'Infinity', 'NaN', '1e3', '12.345', 'abc']) assert.equal(parsePriceQuery(input), '')
  assert.equal(parsePriceQuery(undefined), '')
})
test('price ranges allow open ends and reject reversed or negative values', () => {
  assert.equal(validatePriceRange('', '50'), '')
  assert.equal(validatePriceRange('0', ''), '')
  assert.equal(validatePriceRange('10', '10'), '')
  assert.ok(validatePriceRange('50', '10'))
  assert.ok(validatePriceRange('-1', '10'))
  assert.ok(validatePriceRange('1.001', '10'))
})
