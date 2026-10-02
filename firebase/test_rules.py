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
check("saving requires reason","PUT",sv,{**saving,"text":"  "},"alice",allowed=False)
check("saving with reason accepted","PUT",sv,saving,"alice")
goal={**entry,"id":"shared-goal","type":"goal","amount":100000,"text":"Trip","date":"2027-01-01","fields":{"reason":"Anniversary trip"}}
gp="rooms/"+room+"/items/goal/shared-goal"
check("goal with reason and date accepted","PUT",gp,goal,"alice")
check("goal without reason rejected","PUT",gp,{**goal,"version":2,"fields":{}},"alice",allowed=False)
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
print(str(count)+" rules checks passed.")

