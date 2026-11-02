#!/usr/bin/env python3
"""Destructive end-to-end smoke check against a running deployment. No dependencies."""
import argparse
import concurrent.futures
import datetime as dt
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

parser = argparse.ArgumentParser(description='WARNING: resets and permanently deletes ALL bookings and slots at the target.')
parser.add_argument('--base-url', required=True)
parser.add_argument('--allow-reset', action='store_true', help='Explicitly authorize deletion of ALL target data')
args = parser.parse_args()
if not args.allow_reset:
    parser.error('Refusing to delete data without --allow-reset')
key = os.environ.get('API_KEY')
if not key:
    parser.error('Set API_KEY in the environment; never pass it on the command line')
base = args.base_url.rstrip('/')
if urllib.parse.urlsplit(base).scheme not in ('http', 'https'):
    parser.error('Expected an HTTP(S) base URL')

def request(method, path, body=None, intent=None, authenticated=True):
    headers = {'Content-Type': 'application/json'}
    if authenticated:
        headers['X-API-Key'] = key
    if intent:
        headers['Idempotency-Key'] = intent
    req = urllib.request.Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        result = urllib.request.urlopen(req, timeout=30)
    except urllib.error.HTTPError as error:
        result = error
    with result:
        raw = result.read().decode()
        return result.status, raw, result.headers

def expect(status, result):
    assert result[0] == status, f'Expected HTTP {status}, received {result[0]}'
    if status >= 400:
        assert set(json.loads(result[1])) == {'error'}
    return result

def new_booking(slot, email='demo@example.com'):
    return {'slot_id': slot, 'name': ' Ada Obi ', 'email': email}

print('WARNING: deleting all target bookings, slots, replay records, and rate-limit attempts.')
start = dt.datetime.now(dt.timezone.utc).replace(microsecond=0) + dt.timedelta(days=7)
slots = [{'id': f's{i}', 'provider': 'Dr Ade' if i % 2 else 'Dr Obi', 'start': (start + dt.timedelta(hours=i)).isoformat(), 'end': (start + dt.timedelta(hours=i, minutes=30)).isoformat()} for i in range(8)]
slots.append({'id': 'past', 'provider': 'Dr Past', 'start': '2020-01-01T09:00:00Z', 'end': '2020-01-01T09:30:00Z'})
expect(401, request('GET', '/slots', authenticated=False))
expect(200, request('POST', '/reset', {'slots': slots}))
assert len(json.loads(expect(200, request('GET', '/slots'))[1])['slots']) == 9
assert len(json.loads(expect(200, request('GET', '/slots?provider=Dr%20Ade'))[1])['slots']) == 4
intent = str(uuid.uuid4())
first = expect(201, request('POST', '/bookings', new_booking('s0'), intent))
booking = json.loads(first[1])
assert set(booking) == {'id', 'slot_id', 'name', 'email', 'status', 'created_at'}
assert booking['name'] == 'Ada Obi'
assert expect(201, request('POST', '/bookings', new_booking('s0'), intent))[1] == first[1]
expect(422, request('POST', '/bookings', new_booking('s1'), intent))
expect(200, request('GET', '/bookings/' + booking['id']))
expect(409, request('POST', '/bookings', new_booking('s0', 'competitor@example.com')))
expect(200, request('DELETE', '/bookings/' + booking['id']))
expect(409, request('DELETE', '/bookings/' + booking['id']))
expect(201, request('POST', '/bookings', new_booking('s0', 'rebook@example.com')))
expect(400, request('POST', '/bookings', new_booking('past', 'past@example.com')))
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    results = list(pool.map(lambda i: request('POST', '/bookings', new_booking('s1', f'parallel{i}@example.com')), range(8)))
assert sorted(r[0] for r in results) == [201] + [409] * 7
for _ in range(5):
    expect(404, request('POST', '/bookings', new_booking('missing', 'rate@example.com')))
limited = expect(429, request('POST', '/bookings', new_booking('s2', 'rate@example.com')))
assert int(limited[2]['Retry-After']) > 0
page = json.loads(expect(200, request('GET', '/bookings?email=DEMO@example.com&limit=1'))[1])
assert len(page['bookings']) == 1 and page['next_cursor'] is None
expect(400, request('GET', '/bookings?email=demo@example.com&cursor=invalid'))
page_html = expect(200, request('GET', '/'))[1]
assert 'Available appointments' in page_html and key not in page_html
print('PASS: API authentication, reset, slots, booking, replay, conflict, cancellation, concurrency, pagination, rate limiting, and HTML page.')
print('Target retains demonstration data. Do not reset or redeploy after assessment submission.')
