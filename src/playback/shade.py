"""Relocate only protobuf constant-pool names; preserve every other class-file byte."""
import pathlib,zipfile,struct,json,hashlib
p=pathlib.Path(__file__).parent
old=b'com/google/protobuf';new=b'app/revanced/com/google/protobuf'
def shade(data):
 if data[:4]!=b'\xca\xfe\xba\xbe':raise ValueError('not class')
 count=struct.unpack_from('>H',data,8)[0];out=bytearray(data[:10]);i=1;pos=10
 sizes={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
 while i<count:
  tag=data[pos];out.append(tag);pos+=1
  if tag==1:
   n=struct.unpack_from('>H',data,pos)[0];pos+=2;raw=data[pos:pos+n];pos+=n
   raw=raw.replace(old,new).replace(b'com.google.protobuf',b'app.revanced.com.google.protobuf');out+=struct.pack('>H',len(raw))+raw
  else:
   n=sizes[tag];out+=data[pos:pos+n];pos+=n
   if tag in (5,6):i+=1
  i+=1
 out+=data[pos:];return bytes(out)
with zipfile.ZipFile(p/'helpers-shaded.jar','w',compression=zipfile.ZIP_DEFLATED) as out:
 for f in (p/'helper-classes').rglob('*.class'):out.writestr(str(f.relative_to(p/'helper-classes')),shade(f.read_bytes()))
 with zipfile.ZipFile(p/'protobuf-javalite-4.35.1.jar') as runtime:
  for name in runtime.namelist():
   if name.startswith('com/google/protobuf/') and name.endswith('.class'):out.writestr(name.replace('com/google/protobuf','app/revanced/com/google/protobuf'),shade(runtime.read(name)))
print('Shaded classes',len(zipfile.ZipFile(p/'helpers-shaded.jar').namelist()))
print('protobuf_sha256',hashlib.sha256((p/'protobuf-javalite-4.35.1.jar').read_bytes()).hexdigest())
