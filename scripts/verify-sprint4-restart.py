#!/usr/bin/env python3
"""Verify persistence after restart of an existing isolated Compose demo (no volume deletion)."""
import argparse
import base64
import hashlib
import json
import subprocess
import time
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--project', required=True, help='Existing isolated Compose project')
parser.add_argument('--url', required=True, help='Frontend origin, e.g. http://127.0.0.1:18092')
args = parser.parse_args()
compose = ['docker', 'compose', '-p', args.project]
tables = ['montage_execution', 'containment_task', 'execution_event', 'lot',
          'mix_request', 'non_conformity', 'inventory_hold', 'audit_event', 'mix_event', 'command_result']

def snapshot():
    result = {}
    for table in tables:  # Internal allowlist, not caller-supplied SQL.
        sql = f"select coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text),'[]')::text from {table} t"
        raw = subprocess.check_output(compose + ['exec', '-T', 'db', 'psql', '-U', 'proco', '-d', 'proco', '-At', '-c', sql])
        rows = json.loads(raw)
        result[table] = {'rows': len(rows), 'sha256': hashlib.sha256(raw).hexdigest()}
    return result

# Sprint 5 extends the same read-only snapshot without changing the Sprint 4 invocation.
exists = subprocess.check_output(compose + ['exec', '-T', 'db', 'psql', '-U', 'proco', '-d', 'proco', '-At', '-c', "select to_regclass('ola_rule') is not null"]).strip()
if exists == b't':
    tables.append('ola_rule')
before = snapshot()
assert before['montage_execution']['rows'] > 0, 'Run Sprint 4 E2E fixtures first'
assert before['containment_task']['rows'] > 0, 'No containment data to verify'
subprocess.run(compose + ['restart'], check=True)
ready = False
for _ in range(45):
    try:
        request = urllib.request.Request(args.url.rstrip('/') + '/api/me', headers={
            'Authorization': 'Basic ' + base64.b64encode(b'almacen:Simulacion123!').decode()})
        with urllib.request.urlopen(request, timeout=2) as response:
            ready = response.status == 200
        if ready:
            break
    except (OSError, TimeoutError):
        pass
    time.sleep(1)
assert ready, 'Application did not become ready after restart'
after = snapshot()
assert before == after, json.dumps({'before': before, 'after': after}, indent=2)
print(json.dumps({'result': 'PASS', 'project': args.project, 'preserved': after}, indent=2))
