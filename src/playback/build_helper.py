#!/usr/bin/env python3
"""Build vendored ReVanced playback helpers; performs no player requests.
Android/JSONObject stubs supply javac symbols only and are never packaged.
"""
import argparse,hashlib,json,os,pathlib,shutil,subprocess,sys,urllib.request
ROOT=pathlib.Path(__file__).resolve().parent
URL='https://repo.maven.apache.org/maven2/com/google/protobuf/protobuf-javalite/4.35.1/protobuf-javalite-4.35.1.jar'
SHA='45d3769189888e491ab7d58125f2014c2a86fb8104c7aa3878c723943fce7a14'
def run(args):subprocess.run([str(a) for a in args],check=True)
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--d8-jar',type=pathlib.Path,required=True);parser.add_argument('--output-dir',type=pathlib.Path,required=True);args=parser.parse_args()
 global ROOT
 original=ROOT; ROOT=args.output_dir.resolve();ROOT.mkdir(parents=True,exist_ok=False)
 for folder in ['helper-src','compile-stubs','proto']:shutil.copytree(original/folder,ROOT/folder)
 shutil.copyfile(original/'shade.py',ROOT/'shade.py')
 runtime=ROOT/'protobuf-javalite-4.35.1.jar'
 if not runtime.exists():runtime.write_bytes(urllib.request.urlopen(URL,timeout=60).read())
 if digest(runtime)!=SHA:raise RuntimeError('Protobuf runtime digest mismatch')
 if not args.d8_jar.is_file():raise RuntimeError('D8 jar not found')
 if subprocess.check_output(['protoc','--version'],text=True).strip()!='libprotoc 35.1':raise RuntimeError('Use protoc35.1 to reproduce generated Java')
 proto=ROOT/'proto';sources=ROOT/'helper-src';paths=[proto/'app/revanced/extension/shared/innertube'/f for f in ['player_response.proto','reel_item_watch_response.proto']]
 run(['protoc','-I',proto,'--java_out=lite:'+str(sources),*paths])
 for name in ['stub-classes','helper-classes','helper-dex']:
  target=ROOT/name
  if target.exists():shutil.rmtree(target)
  target.mkdir()
 (ROOT/'stubs.list').write_text('\n'.join(str(f) for f in sorted((ROOT/'compile-stubs').rglob('*.java'))))
 (ROOT/'sources.list').write_text('\n'.join(str(f) for f in sorted(sources.rglob('*.java'))))
 run(['javac','--release','8','-Xlint:-options','-d',ROOT/'stub-classes','@'+str(ROOT/'stubs.list')])
 run(['javac','--release','17','-cp',str(ROOT/'stub-classes')+os.pathsep+str(runtime),'-d',ROOT/'helper-classes','@'+str(ROOT/'sources.list')])
 run([sys.executable,ROOT/'shade.py'])
 run(['java','-cp',args.d8_jar,'com.android.tools.r8.D8','--min-api','26','--output',ROOT/'helper-dex',ROOT/'helpers-shaded.jar'])
 output=ROOT/'helper-dex/classes.dex'
 metadata={'protobuf_runtime_sha256':digest(runtime),'d8_jar_sha256':digest(args.d8_jar),'helper_dex_sha256':digest(output),'helper_dex_bytes':output.stat().st_size,'runtime_verified':False,'java_version':subprocess.run(['java','-version'],capture_output=True,text=True).stderr.strip()}
 (ROOT/'helper-build.json').write_text(json.dumps(metadata,indent=2));print(json.dumps(metadata,indent=2))
if __name__=='__main__':main()
