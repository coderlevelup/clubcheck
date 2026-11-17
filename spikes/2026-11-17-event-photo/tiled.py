import os
import numpy as np, cv2, torch, torchvision, json
from ultralytics import YOLO
img_path=os.environ['PHOTO']
im=cv2.imread(img_path); H,W=im.shape[:2]
m=YOLO('/tmp/spike/models/yolov8s.pt')
def run(conf, tiles, imgsz=1280, iou=0.5):
    boxes=[];scores=[]
    def add(crop,ox,oy):
        r=m.predict(crop,imgsz=imgsz,conf=conf,classes=[0],verbose=False)[0]
        for b,s in zip(r.boxes.xyxy.cpu().numpy(),r.boxes.conf.cpu().numpy()):
            boxes.append([b[0]+ox,b[1]+oy,b[2]+ox,b[3]+oy]);scores.append(s)
    add(im,0,0)
    if tiles:
        n=tiles; ov=0.2
        tw=int(W/(n-(n-1)*ov)); th=int(H/(n-(n-1)*ov))
        for i in range(n):
            for j in range(n):
                x=int(i*tw*(1-ov)); y=int(j*th*(1-ov))
                add(im[y:y+th,x:x+tw],x,y)
    b=torch.tensor(boxes,dtype=torch.float32); s=torch.tensor(scores)
    keep=torchvision.ops.nms(b,s,iou)
    return b[keep].numpy(), s[keep].numpy()
if __name__=='__main__':
    import json
    res={}
    for conf in (0.35,0.25,0.15):
        b,s=run(conf,3); res[conf]=len(b); print('conf',conf,'tiles 3 count',len(b))
        if conf==0.25: np.save('/tmp/spike/out/boxes.npy',np.c_[b,s])
    json.dump(dict(low=res[0.35],point=res[0.25],high=res[0.15]),open('/tmp/spike/out/range.json','w'))
    print('range',res[0.35],'to',res[0.15],'point',res[0.25])
