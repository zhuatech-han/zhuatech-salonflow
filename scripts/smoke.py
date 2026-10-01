#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""可丢弃MySQL实例的真实HTTP美业验收；绝不连接真实邮箱。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse,json,uuid,urllib.request,urllib.error,http.cookiejar,os,datetime,concurrent.futures
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',required=True);p.add_argument('--env',required=True);p.add_argument('--allow-test-writes',action='store_true');p.add_argument('--state',help='Private browser QA state file');a=p.parse_args()
if not a.allow_test_writes:raise SystemExit('Use --allow-test-writes only with a disposable database.')
env=dict(line.split('=',1) for line in Path(a.env).read_text().splitlines() if '=' in line and not line.startswith('#'))
base=a.base.rstrip('/');suffix=uuid.uuid4().hex[:8];count=0
class Client:
    """同源Cookie与CSRF封装，仅用于隔离测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    def __init__(self):self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.token=None
    def call(self,path,body=None,method=None,status=200):
        global count
        if body is not None and not self.token:self.token=self.call('/auth/csrf')
        headers={'Content-Type':'application/json'}
        if body is not None:headers[self.token['header']]=self.token['token']
        req=urllib.request.Request(base+('' if path.startswith('/actuator/') else '/api')+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method or ('GET' if body is None else 'POST'))
        try:r=self.opener.open(req,timeout=30)
        except urllib.error.HTTPError as e:r=e
        raw=r.read().decode('utf-8-sig');assert r.status==status,(path,r.status,raw)
        count+=1
        try:return json.loads(raw)
        except json.JSONDecodeError:return raw
    def login(self,user,password):self.call('/auth/login',{'username':user,'password':password});self.token=None
admin=Client();admin.login(env.get('ADMIN_USERNAME','admin'),env['ADMIN_PASSWORD'])
assert admin.call('/actuator/health')['status']=='UP'
roles=admin.call('/lists/roles?size=100')['items']
def user(name,role):
    rid=next(x['id'] for x in roles if role in x['name'])
    data={'username':name,'displayName':name,'password':'Aa9'+uuid.uuid4().hex,'roleId':rid,'departmentId':1,'enabled':True}
    ident=admin.call('/admin/users',data)['id'];client=Client();client.login(name,data['password']);return data,ident,client
stydata,styid,sty=user('stylist'+suffix,'Stylist');otherdata,otherid,other=user('other'+suffix,'Stylist');deskdata,deskid,desk=user('desk'+suffix,'Reception')
cat=admin.call('/master/categories',{'name':'TEST 美发 / Hair','departmentId':1,'enabled':True})['id']
services=[]
for code,name,price,duration,buffer in [('CUT','TEST 剪发 / Haircut','100',30,15),('COLOR','TEST 染发 / Colour','280',90,15),('CARE','TEST 护理 / Treatment','160',45,15)]:
    services.append(admin.call('/master/services',{'code':'TEST-'+code+'-'+suffix,'name':name,'categoryId':cat,'departmentId':1,'enabled':True,'price':price,'durationMinutes':duration,'bufferMinutes':buffer,'resourceRequired':True})['id'])
rooms=[]
for code,name in [('SEAT','TEST 座位 / Seat'),('ROOM','TEST 房间 / Room')]:rooms.append(admin.call('/master/resources',{'code':'TEST-'+code+'-'+suffix,'name':name,'departmentId':1,'enabled':True})['id'])
staff=[]
for name,ident in [('TEST 陈 / Chen',styid),('TEST 林 / Lin',otherid)]:
    person=admin.call('/master/staff',{'name':name,'accountId':ident,'departmentId':1,'enabled':True,'services':services})['id'];staff.append(person)
    for d in range(1,8):admin.call('/master/shifts',{'staffId':person,'departmentId':1,'weekday':d,'startMinute':0,'endMinute':1440,'breakStart':0,'breakEnd':0})
clients=[]
for n in range(3):clients.append(admin.call('/master/customers',{'name':'TEST 客户 '+str(n+1)+' / Client','departmentId':1,'enabled':True,'email':'test-'+str(n)+'@example.invalid','emailConsent':True})['id'])
now=datetime.datetime.now(datetime.timezone.utc);start=datetime.datetime.fromtimestamp((int(now.timestamp())//900+1)*900,datetime.timezone.utc)
def iso(t):return t.isoformat().replace('+00:00','Z')
def body(c,person,room,t,service=services[0]):return {'customerId':c,'staffId':person,'resourceId':room,'serviceId':service,'startsAt':iso(t),'requestKey':uuid.uuid4().hex,'note':'TEST 虚构验收 / Fictional acceptance'}
v=body(clients[0],staff[0],rooms[0],start);o=admin.call('/appointments',v);ident=o['id'];assert admin.call('/appointments',v)['id']==ident
admin.call('/appointments',body(clients[1],staff[0],rooms[1],start),status=409)
admin.call('/appointments',body(clients[1],staff[1],rooms[0],start),status=409)
admin.call('/appointments',body(clients[0],staff[1],rooms[1],start),status=409)
def detail(id=ident):return admin.call('/appointments/'+str(id))
def command(id=ident,**kw):return {'revision':detail(id)['appointment']['revision'],'requestKey':uuid.uuid4().hex,**kw}
def act(action,kw=None,c=admin,status=200,id=ident):return c.call(f'/appointments/{id}/{action}',command(id,**(kw or {})),status=status)
act('checkout',{'discount':'0'},status=409)
act('arrive');act('start',c=admin,status=403);act('start',c=sty);act('finish',{'note':'TEST 服务完成 / Service finished'},c=sty)
act('checkout',{'discount':'10','note':'TEST 折扣 / Discount'})
# Real MySQL contention: two separate sessions carry the same revision.
parallel=[]
for n in range(2):
    c=Client();c.login(env.get('ADMIN_USERNAME','admin'),env['ADMIN_PASSWORD']);c.call('/auth/csrf');parallel.append(c)
commands=[command(amount='40',method='CASH',reference='TEST-CASH-'+str(n)) for n in range(2)]
def contested(pair):
    c,cmd=pair
    try:c.call(f'/appointments/{ident}/pay',cmd);return 200,cmd
    except AssertionError as e:
        assert e.args[0][1]==409,e.args;return 409,cmd
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(contested,zip(parallel,commands)))
assert sorted(code for code,_ in results)==[200,409],results
paid=next(cmd for code,cmd in results if code==200);admin.call(f'/appointments/{ident}/pay',paid)
assert detail()['appointment']['paid']==40 and len(detail()['payments'])==1
act('pay',{'amount':'51','method':'CASH','reference':'TEST-EXCESS'},status=409)
act('pay',{'amount':'50','method':'BANK','reference':'TEST-BANK'})
source=detail()['payments'][0]['id'];refund=command(amount='20',sourceId=source,reference='TEST-REFUND',note='TEST 局部退款 / Partial refund')
admin.call(f'/appointments/{ident}/refund',refund);admin.call(f'/appointments/{ident}/refund',refund)
act('refund',{'amount':'21','sourceId':source,'reference':'TEST-EXCESS','note':'TEST'},status=409)
act('refund',{'amount':'1','sourceId':source,'reference':'TEST-ACL','note':'TEST'},c=desk,status=403)
d=detail();assert d['appointment']['status']=='COMPLETED' and d['appointment']['total']==90 and d['appointment']['paid']==90 and d['appointment']['refunded']==20 and len(d['payments'])==3
local=(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=8)).date().isoformat();r=admin.call('/reports?from='+local+'&to='+local);assert r['receipts']==90 and r['refunds']==20 and r['netReceipts']==70,r
assert 'zhuatech' not in admin.call('/reports.csv?from='+local+'&to='+local)
Client().call('/appointments/'+str(ident),status=401);other.call('/appointments/'+str(ident),status=403);sty.call('/lists/users',status=403)
# Tomorrow's real agenda and self-booking, while preserving original snapshot on reschedule.
tomorrow=(now+datetime.timedelta(days=1,hours=8)).date()
future=datetime.datetime.combine(tomorrow,datetime.time(9,0),datetime.timezone(datetime.timedelta(hours=8))).astimezone(datetime.timezone.utc)
confirmed=admin.call('/appointments',body(clients[1],staff[0],rooms[0],future))['id']
admin.call('/master/services/'+str(services[0]),{'code':'TEST-CUT-'+suffix,'name':'TEST 剪发 / Haircut','categoryId':cat,'departmentId':1,'enabled':True,'price':'150','durationMinutes':45,'bufferMinutes':15,'resourceRequired':True},method='PUT')
slots=admin.call(f'/slots?serviceId={services[0]}&staffId={staff[0]}&resourceId={rooms[0]}&date={tomorrow}&appointmentId={confirmed}')
same=next(x for x in slots if x['startsAt']==iso(future));assert same['endsAt']==iso(future+datetime.timedelta(minutes=30))
act('reschedule',{'staffId':staff[0],'resourceId':rooms[0],'startsAt':iso(future+datetime.timedelta(minutes=15))},id=confirmed)
assert detail(confirmed)['appointment']['price']==100 and detail(confirmed)['appointment']['durationMinutes']==30
for c,person,room,t,service in [(clients[2],staff[1],rooms[1],future+datetime.timedelta(hours=1),services[2]),(clients[0],staff[0],rooms[0],future+datetime.timedelta(hours=3),services[1])]:admin.call('/appointments',body(c,person,room,t,service))
customer=Client();custdata={'username':'client'+suffix,'password':'Aa9'+uuid.uuid4().hex,'name':'TEST 自助客户 / Self-booking','departmentId':1,'roleId':1,'email':'client@example.invalid','emailConsent':False};customer.call('/public/register',custdata);customer.login(custdata['username'],custdata['password']);profile=customer.call('/auth/me');assert profile['kind']=='CUSTOMER' and 'admin' not in profile['permissions']
cs=customer.call(f'/slots?serviceId={services[0]}&staffId={staff[1]}&resourceId={rooms[1]}&date={tomorrow}')
chosen=next(x for x in cs if 'T04:00:' in x['startsAt'])
own=customer.call('/appointments',body(clients[0],staff[1],rooms[1],datetime.datetime.fromisoformat(chosen['startsAt'].replace('Z','+00:00'))))['id']
assert detail(own)['appointment']['customerId']!=clients[0];customer.call('/appointments/'+str(ident),status=403)
second=Client();seconddata={**custdata,'username':'client2'+suffix};second.call('/public/register',seconddata);second.login(seconddata['username'],seconddata['password']);second.call('/appointments/'+str(own),status=403)
assert all(x['id']==own for x in customer.call('/lists/appointments')['items'])
if a.state:
    fd=os.open(a.state,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f:json.dump({'base':base,'admin':{'username':env.get('ADMIN_USERNAME','admin'),'password':env['ADMIN_PASSWORD']},'stylist':stydata,'other':otherdata,'desk':deskdata,'customer':custdata,'settled':ident,'confirmed':confirmed,'own':own,'date':str(tomorrow),'staff':staff,'services':services,'resources':rooms,'clients':clients},f)
print(f'PASS: {count} HTTP checks; fresh MySQL booking, service, parallel partial payments, original refunds, snapshots, customer self-booking and permissions verified')
