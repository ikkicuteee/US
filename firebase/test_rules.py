"""Rules integration checks against the local Realtime Database emulator only.
Run: python firebase/test_rules.py (emulator listening on 127.0.0.1:9000).
No production database or accounts are touched.
"""
import json
from pathlib import Path
import time
import urllib.request
import urllib.error
import urllib.parse

BASE = "http://127.0.0.1:9000"
NS = "demo-us-spark"
def request(method, path, value=None, user=None, admin=False, etag=None):
    headers = {"Content-Type": "application/json"}
    if admin:
        headers["Authorization"] = "Bearer owner"
    elif user:
        # Admin authentication with an explicit rules identity override; emulator only.
        headers["Authorization"] = "Bearer owner"
        auth_override = urllib.parse.quote(json.dumps({"uid":user,"token":{"sub":user,"firebase":{"sign_in_provider":"password"}}}))
    if etag: headers["if-match"] = etag
    req = urllib.request.Request(BASE+"/"+path+".json?ns="+NS+("&auth_variable_override="+auth_override if user else ""),
        data=json.dumps(value).encode() if method in ("PUT","PATCH") else None,
        headers=headers, method=method)
    try:
        with urllib.request.urlopen(req,timeout=10) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()

count=0
def check(name, method, path, value=None, user=None, allowed=True, admin=False):
    global count
    status, body=request(method,path,value,user,admin)
    ok=200<=status<300
    assert ok==allowed, (name,status,body)
    count+=1
    print("PASS",name)
    return body

rules=json.loads(Path(__file__).with_name("database.rules.json").read_text(encoding="utf-8-sig"))
check("rules compile", "PUT",".settings/rules",rules,admin=True)
check("clear test namespace", "DELETE","",admin=True)
room="a"*64
pair={"owner":"alice","guest":"","expires":int(time.time()*1000)+3600000}
check("unauthenticated invite denied","PUT","pairings/"+room,pair,allowed=False)
check("owner creates invitation","PUT","pairings/"+room,pair,"alice")
check("cannot list invitations","GET","pairings",user="eve",allowed=False)
check("invited account can read unclaimed invitation","GET","pairings/"+room,user="bob")
check("owner links space","PUT","users/alice/room",room,"alice")
check("outsider cannot self-link","PUT","users/eve/room",room,"eve",allowed=False)
check("outsider cannot read room","GET","rooms/"+room,user="eve",allowed=False)
check("guest claims invitation","PUT","pairings/"+room,{**pair,"guest":"bob"},"bob")
check("guest links space","PUT","users/bob/room",room,"bob")
check("third account cannot claim","PUT","pairings/"+room,{**pair,"guest":"eve"},"eve",allowed=False)
check("third account cannot inspect claimed invitation","GET","pairings/"+room,user="eve",allowed=False)
check("owner cannot replace guest","PUT","pairings/"+room,{**pair,"guest":"eve"},"alice",allowed=False)
check("owner cannot reopen claimed invitation","PUT","pairings/"+room,pair,"alice",allowed=False)
check("cannot change owner","PUT","pairings/"+room,{**pair,"owner":"eve","guest":"bob"},"bob",allowed=False)
check("cannot write other user's pointer","PUT","users/alice/room",room,"bob",allowed=False)

now=int(time.time()*1000)
entry={"id":"note","type":"message","author":"alice","editor":"alice","version":1,
       "time":now,"text":"Hello","date":"","done":False,"deleted":False,"amount":0}
path="rooms/"+room+"/items/message/note"
check("member writes message","PUT",path,entry,"alice")
check("partner reads message","GET",path,user="bob")
check("outsider cannot read message","GET",path,user="eve",allowed=False)
check("anonymous cannot read message","GET",path,allowed=False)
check("outsider cannot write message","PUT",path,{**entry,"version":2,"editor":"eve"},"eve",allowed=False)
check("partner cannot rewrite other's message","PUT",path,{**entry,"version":2,"editor":"bob"},"bob",allowed=False)
check("message author can edit","PUT",path,{**entry,"version":2,"text":"Updated"},"alice")
check("stale version rejected","PUT",path,entry,"alice",allowed=False)
check("attachment field rejected","PUT",path,{**entry,"version":3,"fileId":"x"},"alice",allowed=False)
check("oversized message rejected","PUT",path,{**entry,"version":3,"text":"x"*10001},"alice",allowed=False)

event={**entry,"id":"plan","type":"event"}
ep="rooms/"+room+"/items/event/plan"
check("owner creates plan","PUT",ep,event,"alice")
check("partner completes shared plan","PUT",ep,{**event,"version":2,"editor":"bob","done":True},"bob")
check("equal version deterministic winner","PUT",ep,{**event,"version":2,"editor":"alice"},"alice",allowed=False)
check("newer version wins","PUT",ep,{**event,"version":3},"alice")
check("plan tombstone","PUT",ep,{**event,"version":4,"deleted":True},"alice")
check("hard deletion denied","DELETE",ep,user="alice",allowed=False)
profile={**entry,"id":"profile-alice","type":"profile","fields":{"avatar":"a"*32000,"hobbies":"walking"}}
pp="rooms/"+room+"/items/profile/profile-alice"
check("bounded avatar allowed","PUT",pp,profile,"alice")
check("oversized avatar denied","PUT",pp,{**profile,"version":2,"fields":{"avatar":"a"*32001}},"alice",allowed=False)
check("partner profile edit denied","PUT",pp,{**profile,"version":2,"editor":"bob"},"bob",allowed=False)
check("unknown profile field denied","PUT",pp,{**profile,"version":2,"fields":{"anything":"x"}},"alice",allowed=False)

lp="rooms/"+room+"/locations/alice"
loc={"latitude":14.6,"longitude":121.0,"accuracy":8,"time":now,"updatedAt":{".sv":"timestamp"}}
check("own live position allowed","PUT",lp,loc,"alice")
check("partner reads location","GET",lp,user="bob")
check("outsider cannot read location","GET",lp,user="eve",allowed=False)
check("partner cannot spoof location","PUT",lp,loc,"bob",allowed=False)
check("invalid coordinates rejected","PUT",lp,{**loc,"latitude":91},"alice",allowed=False)
check("location history field denied","PUT",lp,{**loc,"route":[{"latitude":14.5}]},"alice",allowed=False)
check("stop sharing removes location","DELETE",lp,user="alice")
check("own presence allowed","PUT","rooms/"+room+"/presence/alice",True,"alice")
check("spoofed presence denied","PUT","rooms/"+room+"/presence/alice",True,"bob",allowed=False)
check("disconnect can remove presence","DELETE","rooms/"+room+"/presence/alice",user="alice")

# Planner, location status and required savings details.
timed={**event,"id":"timed","date":"2026-10-02","fields":{"todo":"true","repeat":"monthly","at":"23:59","done-2026-10-02":"true"}}
tp="rooms/"+room+"/items/event/timed"
check("monthly plan with time and occurrence check allowed","PUT",tp,timed,"alice")
check("partner can uncheck occurrence","PUT",tp,{**timed,"version":2,"editor":"bob","fields":{**timed["fields"],"done-2026-10-02":"false"}},"bob")
for invalid in ["24:00","9:30","12:60"]:
    check("invalid plan time "+invalid,"PUT",tp,{**timed,"version":3,"fields":{"at":invalid}},"alice",allowed=False)
check("unknown repeat rejected","PUT",tp,{**timed,"version":3,"fields":{"repeat":"weekly"}},"alice",allowed=False)
check("annual repeat accepted","PUT",tp,{**timed,"version":3,"fields":{"repeat":"annual","at":"00:00"}},"alice")
check("unknown event field rejected","PUT",tp,{**timed,"version":4,"fields":{"surprise":"x"}},"alice",allowed=False)
status={**entry,"id":"locationstatus-alice","type":"locationstatus","text":"Saving battery","fields":{"sharing":"false"}}
sp="rooms/"+room+"/items/locationstatus/locationstatus-alice"
check("own stop reason allowed","PUT",sp,status,"alice")
check("partner can read stop reason","GET",sp,user="bob")
check("partner cannot spoof stop reason","PUT",sp,{**status,"version":2,"editor":"bob"},"bob",allowed=False)
check("outsider cannot read stop reason","GET",sp,user="eve",allowed=False)
check("overlong stop reason rejected","PUT",sp,{**status,"version":2,"text":"x"*201},"alice",allowed=False)
check("resume sharing clears reason","PUT",sp,{**status,"version":2,"text":"","fields":{"sharing":"true"}},"alice")
saving={**entry,"id":"saving","type":"saving","amount":100,"text":"Weekly contribution"}
sv="rooms/"+room+"/items/saving/saving"
check("deduction requires reason","PUT",sv,{**saving,"amount":-100,"text":"  "},"alice",allowed=False)
check("saving with reason accepted","PUT",sv,saving,"alice")
check("deposit without reason accepted","PUT",sv,{**saving,"version":2,"text":""},"alice")
check("deduction with reason accepted","PUT",sv,{**saving,"version":3,"amount":-100,"text":"Bus fare"},"alice")
goal={**entry,"id":"shared-goal","type":"goal","amount":100000,"text":"Trip","date":"2027-01-01","fields":{"reason":"Anniversary trip"}}
gp="rooms/"+room+"/items/goal/shared-goal"
check("goal with reason and date accepted","PUT",gp,goal,"alice")
simplegoal={**goal,"id":"simple-goal","fields":{}}
simplepath="rooms/"+room+"/items/goal/simple-goal"
check("goal without reason accepted","PUT",simplepath,simplegoal,"alice")
check("goal without reason can be archived","PUT",simplepath,{**simplegoal,"version":2,"fields":{"outcome":"success","finalBalance":"100000","closedAt":str(now)}},"alice")
check("goal without target date rejected","PUT",gp,{**goal,"version":2,"date":""},"alice",allowed=False)
check("partner can update goal","PUT",gp,{**goal,"version":2,"editor":"bob","fields":{"reason":"Saving for flights"}},"bob")

# Closing retains the original goal as immutable history.
closed={**goal,"version":3,"editor":"bob","fields":{"reason":"Saving for flights","outcome":"success","finalBalance":"100000","closedAt":str(now)}}
check("partial goal history rejected","PUT",gp,{**closed,"fields":{"reason":"Trip","outcome":"success"}},"bob",allowed=False)
check("unknown goal outcome rejected","PUT",gp,{**closed,"fields":{**closed["fields"],"outcome":"maybe"}},"bob",allowed=False)
check("invalid final balance rejected","PUT",gp,{**closed,"fields":{**closed["fields"],"finalBalance":"NaN"}},"bob",allowed=False)
check("closing cannot change goal target","PUT",gp,{**closed,"amount":1},"bob",allowed=False)
check("member closes goal into history","PUT",gp,closed,"bob")
check("partner reads archived goal","GET",gp,user="alice")
check("closed goal cannot be reopened","PUT",gp,{**goal,"version":4},"alice",allowed=False)
check("closed goal cannot be overwritten","PUT",gp,{**closed,"version":4,"editor":"alice","text":"Changed"},"alice",allowed=False)
check("closed goal cannot be hidden","PUT",gp,{**closed,"version":4,"editor":"alice","deleted":True},"alice",allowed=False)
check("closed goal cannot be deleted","DELETE",gp,user="bob",allowed=False)
newgoal={**goal,"id":"next-goal"}
ngp="rooms/"+room+"/items/goal/next-goal"
check("new goal does not overwrite history","PUT",ngp,newgoal,"alice")
check("unsuccessful goal can be archived","PUT",ngp,{**newgoal,"version":2,"fields":{"reason":"Trip","outcome":"failure","finalBalance":"20000","closedAt":str(now)}},"alice")
check("outsider cannot read history","GET",gp,user="eve",allowed=False)

expired="b"*64
old={"owner":"alice","guest":"","expires":now-1000}
check("seed expired invitation","PUT","pairings/"+expired,old,admin=True)
check("expired invite unreadable to guest","GET","pairings/"+expired,user="bob",allowed=False)
check("expired invite unclaimable","PUT","pairings/"+expired,{**old,"guest":"bob"},"bob",allowed=False)
check("owner can renew pending invite","PUT","pairings/"+expired,{**old,"expires":now+3600000},"alice")
check("owner cannot delete membership record","DELETE","pairings/"+room,user="alice",allowed=False)
# Two claimants race for one invitation. Exactly one may become the partner.
from concurrent.futures import ThreadPoolExecutor
race="c"*64
racepair={"owner":"alice","guest":"","expires":int(time.time()*1000)+3600000}
check("create race invitation","PUT","pairings/"+race,racepair,"alice")
with ThreadPoolExecutor(max_workers=2) as pool:
    results=list(pool.map(lambda uid: request("PUT","pairings/"+race,{**racepair,"guest":uid},uid)[0], ["bob","eve"]))
assert sum(200 <= code < 300 for code in results) == 1, results
count += 1
print("PASS exactly one concurrent claimant accepted")
cycle_id = "cycle-period-11111111-1111-1111-1111-111111111111"
cycle = {**entry, "id":cycle_id, "type":"cycle", "text":"", "date":"2026-09-01", "fields":{"kind":"period","end":"2026-09-05"}}
cp = "rooms/"+room+"/items/cycle/"+cycle_id
check("member logs cycle period","PUT",cp,cycle,"alice")
check("partner reads cycle period","GET",cp,user="bob")
check("anonymous cannot read cycle period","GET",cp,allowed=False)
check("outsider cannot read cycle period","GET",cp,user="eve",allowed=False)
check("outsider cannot write cycle period","PUT",cp,{**cycle,"version":2,"editor":"eve"},"eve",allowed=False)
check("partner can edit shared cycle","PUT",cp,{**cycle,"version":2,"editor":"bob"},"bob")
check("stale cycle edit denied","PUT",cp,cycle,"alice",allowed=False)
for label, patch in [
    ("invalid cycle date", {"date":"2026-99-45"}),
    ("reversed period dates", {"fields":{"kind":"period","end":"2026-08-31"}}),
    ("unknown cycle field", {"fields":{"kind":"period","end":"","unknown":"x"}}),
    ("cycle kind cannot change", {"fields":{"kind":"settings","predict":"true","length":"28"}}),
    ("cycle cannot affect savings", {"amount":100}),
    ("cycle cannot be a task", {"done":True}),
    ("cycle end is required", {"fields":{"kind":"period"}}),
]:
    check(label,"PUT",cp,{**cycle,"version":3,**patch},"alice",allowed=False)
check("ongoing cycle period allowed","PUT",cp,{**cycle,"version":3,"fields":{"kind":"period","end":""}},"alice")
check("partner can remove cycle period","PUT",cp,{**cycle,"version":4,"editor":"bob","deleted":True},"bob")
daily={**cycle,"id":"cycle-day-2026-09-01","text":"Rested","fields":{"kind":"day","flow":"Heavy","pain":"7","mood":"Okay","energy":"Low","sleep":"Poor","symptoms":"Cramps|Bloating"}}
dp="rooms/"+room+"/items/cycle/"+daily["id"]
check("daily cycle log allowed","PUT",dp,daily,"alice")
check("partner reads daily notes","GET",dp,user="bob")
for key,value in [("pain","11"),("pain","-1"),("flow","invalid"),("symptoms","Unknown"),("sleep","invalid"),("mood","invalid"),("energy","invalid"),("predict","true")]:
    check("invalid daily "+key+" "+value,"PUT",dp,{**daily,"version":2,"fields":{**daily["fields"],key:value}},"alice",allowed=False)
check("daily note limit enforced","PUT",dp,{**daily,"version":2,"text":"x"*1001},"alice",allowed=False)
check("daily date must match key","PUT",dp,{**daily,"version":2,"date":"2026-09-02"},"alice",allowed=False)
check("missing daily field denied","PUT",dp,{**daily,"version":2,"fields":{k:v for k,v in daily['fields'].items() if k!='flow'}},"alice",allowed=False)
check("unlogged daily pain allowed","PUT",dp,{**daily,"version":2,"fields":{**daily['fields'],"pain":"","symptoms":""}},"alice")
config={**cycle,"id":"cycle-settings","date":"","fields":{"kind":"settings","predict":"true","length":"0"}}
scp="rooms/"+room+"/items/cycle/cycle-settings"
check("automatic cycle settings allowed","PUT",scp,config,"alice")
for length in ["-1","366","1.5","abc",""]:
    check("invalid cycle length "+length,"PUT",scp,{**config,"version":2,"fields":{**config['fields'],"length":length}},"alice",allowed=False)
check("partner can disable estimates","PUT",scp,{**config,"version":2,"editor":"bob","fields":{"kind":"settings","predict":"false","length":"28"}},"bob")
check("outsider cannot read cycle settings","GET",scp,user="eve",allowed=False)
check("unauthenticated cycle write denied","PUT",dp,daily,allowed=False)
# Drawing layers belong to their author, including erasure and replacement.
drawing={**entry,"id":"drawing-alice","type":"drawing","text":"","fields":{"image":"aGVsbG8="}}
drawpath="rooms/"+room+"/items/drawing/drawing-alice"
check("author shares drawing","PUT",drawpath,drawing,"alice")
check("partner reads drawing","GET",drawpath,user="bob")
check("outsider cannot read drawing","GET",drawpath,user="eve",allowed=False)
check("anonymous cannot read drawing","GET",drawpath,allowed=False)
check("partner cannot replace drawing","PUT",drawpath,{**drawing,"editor":"bob","version":2},"bob",allowed=False)
check("partner cannot erase drawing","PUT",drawpath,{**drawing,"editor":"bob","version":2,"fields":{"image":""}},"bob",allowed=False)
check("partner cannot delete drawing","PUT",drawpath,{**drawing,"editor":"bob","version":2,"deleted":True},"bob",allowed=False)
check("partner cannot forge drawing author","PUT",drawpath,{**drawing,"author":"bob","editor":"bob","version":2},"bob",allowed=False)
check("other item types cannot collide with drawing IDs","PUT","rooms/"+room+"/items/event/drawing-alice",{**drawing,"type":"event","author":"bob","editor":"bob","version":99,"fields":{}},"bob",allowed=False)
check("outsider cannot write drawing","PUT",drawpath,{**drawing,"editor":"eve","version":2},"eve",allowed=False)
check("drawing size bounded","PUT",drawpath,{**drawing,"version":2,"fields":{"image":"a"*128001}},"alice",allowed=False)
check("unknown drawing fields denied","PUT",drawpath,{**drawing,"version":2,"fields":{"image":"","other":"x"}},"alice",allowed=False)
check("missing drawing image denied","PUT",drawpath,{**drawing,"version":2,"fields":{}},"alice",allowed=False)
check("non base64 drawing denied","PUT",drawpath,{**drawing,"version":2,"fields":{"image":"<image>"}},"alice",allowed=False)
check("drawing cannot affect savings","PUT",drawpath,{**drawing,"version":2,"amount":1},"alice",allowed=False)
check("drawing cannot contain text","PUT",drawpath,{**drawing,"version":2,"text":"x"},"alice",allowed=False)
check("author can clear own drawing","PUT",drawpath,{**drawing,"version":2,"fields":{"image":""}},"alice")
check("author can replace own drawing","PUT",drawpath,{**drawing,"version":3},"alice")
check("author can tombstone own drawing","PUT",drawpath,{**drawing,"version":4,"deleted":True},"alice")
bobdrawing={**drawing,"id":"drawing-bob","author":"bob","editor":"bob"}
bobpath="rooms/"+room+"/items/drawing/drawing-bob"
check("partner has independent drawing","PUT",bobpath,bobdrawing,"bob")
check("alice cannot erase bob drawing","PUT",bobpath,{**bobdrawing,"version":2,"editor":"alice","fields":{"image":""}},"alice",allowed=False)
print(str(count)+" rules checks passed.")

