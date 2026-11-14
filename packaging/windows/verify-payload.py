#!/usr/bin/env python3
"""Smoke-test the exact Compose document rendered by the launcher core; isolated data only."""
import base64
import hashlib
import json
import pathlib
import subprocess
import sys
import time
import urllib.error
import urllib.request

payload = pathlib.Path(sys.argv[1]).resolve()
session = json.loads((payload / 'check/verification-session.json').read_text())
project = 'proco-demo-' + session['Id']
assert len(session['Id']) == 16 and all(c in 'abcdef0123456789' for c in session['Id'])
compose = ['docker', 'compose', '-p', project, '-f', str(payload / 'check/compose.yml')]
base = 'http://127.0.0.1:' + str(session['Port']) + '/api'
http = urllib.request.build_opener(urllib.request.ProxyHandler({}))
def request(path, data=None, key=None):
    headers = {'Authorization': 'Basic ' + base64.b64encode(b'almacen:Simulacion123!').decode()}
    if data is not None:
        headers.update({'Content-Type': 'application/json', 'Idempotency-Key': key})
    with http.open(urllib.request.Request(base + path, data=json.dumps(data).encode() if data is not None else None, headers=headers), timeout=10) as r:
        return json.load(r)
def ready():
    last = 'no response'
    for _ in range(120):
        try:
            if request('/me')['username'] == 'almacen' and len(request('/lots')) >= 5: return
        except (OSError, urllib.error.URLError) as error: last = str(error)
        time.sleep(1)
    raise AssertionError('Application not ready: ' + last)
def snapshot():
    sql = "select jsonb_build_object('lots',(select jsonb_agg(to_jsonb(l) order by id) from lot l),'commands',(select jsonb_agg(to_jsonb(c) order by actor,command_key) from command_result c),'audit',(select jsonb_agg(to_jsonb(a) order by id) from audit_event a))::text"
    return subprocess.check_output(compose + ['exec', '-T', 'db', 'psql', '-U', 'proco', '-d', 'proco', '-At', '-c', sql])
try:
    rendered = json.loads(subprocess.check_output(compose + ['config', '--format', 'json']))
    assert rendered['networks']['default']['internal'] is True
    assert set(rendered['services']['frontend']['networks']) == {'default', 'presentation'}
    assert set(rendered['services']['backend']['networks']) == {'default'}
    assert set(rendered['services']['db']['networks']) == {'default'}
    assert len([p for s in rendered['services'].values() for p in s.get('ports', [])]) == 1
    for service in rendered['services'].values():
        assert service['pull_policy'] == 'never' and 'build' not in service
    subprocess.run(compose + ['up', '-d', '--no-build', '--pull', 'never'], check=True)
    ready()
    lots = request('/lots'); assert len(lots) == 5
    import uuid
    key = str(uuid.uuid4())
    body = {'version': 0, 'orderCode': 'OC-H-001', 'supplierLot': 'INSTALLER-SMOKE-' + key, 'productionDate': '2026-01-01', 'quantity': 1, 'location': 'INSTALLER-SIM', 'reason': 'Packaging verification only', 'evidence': 'INSTALLER-SIM'}
    created = request('/receipts', body, key)
    assert request('/receipts', body, key)['id'] == created['id']
    assert len(request('/lots')) == 6
    migrations = subprocess.check_output(compose + ['exec', '-T', 'db', 'psql', '-U', 'proco', '-d', 'proco', '-At', '-c', 'select count(*) from flyway_schema_history where success']).decode().strip()
    assert migrations == '7'
    before = snapshot()
    subprocess.run(compose + ['stop'], check=True)
    subprocess.run(compose + ['up', '-d', '--no-build', '--pull', 'never'], check=True)
    ready(); assert snapshot() == before
    results = {'result': 'PASS', 'project': project, 'migrations': 7, 'initialLots': 5, 'receiptReplay': 'one lot', 'restartPersistence': 'identical lots/commands/audit', 'snapshotSha256': hashlib.sha256(before).hexdigest(), 'noBuild': True, 'pullPolicy': 'never', 'dataNetwork': 'internal', 'presentationNetwork': 'frontend only', 'publishedPorts': 1, 'platform': 'linux/amd64', 'note': 'Windows Docker Desktop/WSL offline test still requires target laptop.'}
    (payload / 'verification.json').write_text(json.dumps(results, indent=2))
    print(json.dumps(results, indent=2))
except Exception:
    subprocess.run(compose + ['ps', '--all'], check=False)
    subprocess.run(compose + ['logs', '--no-color', '--tail', '120'], check=False)
    raise
finally:
    # Only this uniquely named CI verification project; no -v, data are not erased by this script.
    subprocess.run(compose + ['stop'], check=False)
