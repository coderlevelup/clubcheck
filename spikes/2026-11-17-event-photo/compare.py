import os, numpy as np, cv2, torch, torchvision, sys
from ultralytics import YOLO
m=YOLO('/tmp/spike/models/yolov8s.pt')
def boxes_for(path, tiles):
    im=cv2.imread(path); H,W=im.shape[:2]; B=[];S=[]
    def add(crop,ox,oy):
        r=m.predict(crop,imgsz=1280,conf=0.15,classes=[0],verbose=False)[0]
        for b,s in zip(r.boxes.xyxy.cpu().numpy(),r.boxes.conf.cpu().numpy()): B.append([b[0]+ox,b[1]+oy,b[2]+ox,b[3]+oy]);S.append(float(s))
    add(im,0,0)
    if tiles:
        n=3;ov=.2;tw=int(W/(n-(n-1)*ov));th=int(H/(n-(n-1)*ov))
        for j in range(n):
            for i in range(n):
                x=W-tw if i==n-1 else round(i*tw*(1-ov)); y=H-th if j==n-1 else round(j*th*(1-ov)); add(im[y:y+th,x:x+tw],x,y)
    return np.array(B),np.array(S)
def nms_iou(B,S,thr=.5):
    k=torchvision.ops.nms(torch.tensor(B,dtype=torch.float32),torch.tensor(S,dtype=torch.float32),thr); return k.numpy()
def nms_contain(B,S,thr=.6):
    # suppress a lower-score box if >= thr of ITS area lies inside a kept box (merges clipped partials), plus IoU .5
    order=np.argsort(-S); keep=[]
    for i in order:
        a=B[i]; area=(a[2]-a[0])*(a[3]-a[1]); drop=False
        for j in keep:
            b=B[j]; iw=min(a[2],b[2])-max(a[0],b[0]); ih=min(a[3],b[3])-max(a[1],b[1])
            if iw>0 and ih>0:
                inter=iw*ih; barea=(b[2]-b[0])*(b[3]-b[1])
                if inter/area>=thr or inter/(area+barea-inter)>0.5: drop=True;break
        if not drop: keep.append(i)
    return keep
for spec in os.environ['PHOTOS'].split(';'):   # "label=path;label=path"
    label,path=spec.split('=',1)
    out={}
    for tiles in (False,True):
        B,S=boxes_for(path,tiles)
        for conf in (.35,.25,.15):
            sel=S>=conf; b=B[sel]; s=S[sel]
            out[(tiles,conf,'iou')]=len(nms_iou(b,s)) if len(b) else 0
            out[(tiles,conf,'contain')]=len(nms_contain(b,s)) if len(b) else 0
    print(label)
    for tiles in (False,True):
        for rule in ('iou','contain'):
            print('  tiles' if tiles else '  whole', rule.ljust(8), [out[(tiles,c,rule)] for c in (.35,.25,.15)], '(0.35, 0.25, 0.15)')
