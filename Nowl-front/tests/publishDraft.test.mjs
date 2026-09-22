import test from 'node:test'
import assert from 'node:assert/strict'
import { readDraft, draftKey } from '../src/utils/publishDraft.ts'

test('drafts are isolated by account and never share a guest key', () => {
  assert.notEqual(draftKey(1), draftKey(2))
  assert.equal(draftKey(undefined), null)
})
test('corrupt, expired and unexpected draft fields cannot populate the form', () => {
  assert.equal(readDraft('{broken'), null)
  assert.equal(readDraft(JSON.stringify({ savedAt: 1, form: {} })), null)
  assert.equal(readDraft(JSON.stringify({ savedAt: Date.now(), form: { title: {} } })), null)
  const draft = readDraft(JSON.stringify({ savedAt: Date.now(), form: {
    title: '教材', desc: '笔记少量', price: '15.00', condition: '9', tradeType: 0,
    images: ['/campus/wanxia.jpg', 'javascript:alert(1)', 123], categoryId: 2,
    deliveryFee: '', secret: 'not copied',
  } }))
  assert.equal(draft.form.title, '教材')
  assert.deepEqual(draft.form.images, ['/campus/wanxia.jpg'])
  assert.equal(draft.form.secret, undefined)
})
