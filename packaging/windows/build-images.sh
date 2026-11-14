#!/usr/bin/env bash
set -euo pipefail
# Build this package's application source, never the existing UAT volume.
repo=$(cd "$(dirname "$0")/../.." && pwd)
out=${1:?Usage: build-images.sh OUTPUT_DIRECTORY}
mkdir -p "$out"
out=$(cd "$out" && pwd)
sha=$(git -C "$repo" rev-parse HEAD)
version="1.0.0-${sha:0:12}"
backend="proco-demo-backend:${sha:0:12}"
frontend="proco-demo-frontend:${sha:0:12}"
database="proco-demo-postgres:${sha:0:12}"
docker build --platform linux/amd64 -t "$backend" "$repo/backend"
docker build --platform linux/amd64 -t "$frontend" "$repo/frontend"
printf 'FROM postgres:17-alpine\n' > "$out/database.Dockerfile"
docker build --platform linux/amd64 -t "$database" -f "$out/database.Dockerfile" "$out"
docker image save "$backend" "$frontend" "$database" | gzip > "$out/images.tar.gz"
python3 - "$out" "$version" "$sha" "$backend" "$frontend" "$database" <<'PY'
import hashlib,json,subprocess,sys,pathlib
out,version,sha,*tags=sys.argv[1:];p=pathlib.Path(out)
images=[]
for tag in tags:
 data=json.loads(subprocess.check_output(['docker','image','inspect',tag]))[0]
 assert data['Architecture']=='amd64' and data['Os']=='linux'
 images.append({'Tag':tag,'Id':data['Id']})
h=hashlib.sha256()
with (p/'images.tar.gz').open('rb') as f:
 for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
(p/'manifest.json').write_text(json.dumps({'Version':version,'SourceCommit':sha,'Platform':'linux/amd64','ArchiveSha256':h.hexdigest(),'Backend':images[0],'Frontend':images[1],'Database':images[2]},indent=2))
PY
