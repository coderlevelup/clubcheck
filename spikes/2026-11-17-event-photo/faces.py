import os, numpy as np, cv2, torch, torchvision
from ultralytics import YOLO
im=cv2.imread(os.environ['PHOTO']); H,W=im.shape[:2]
m=YOLO('/tmp/spike/models/face.pt')
def run(conf,n=3,imgsz=1280,ov=0.2):
    B=[];S=[]
    def add(crop,ox,oy):
        r=m.predict(crop,imgsz=imgsz,conf=conf,verbose=False)[0]
        for b,s in zip(r.boxes.xyxy.cpu().numpy(),r.boxes.conf.cpu().numpy()):
            B.append([b[0]+ox,b[1]+oy,b[2]+ox,b[3]+oy]);S.append(s)
    add(im,0,0)
    tw=int(W/(n-(n-1)*ov)); th=int(H/(n-(n-1)*ov))
    for i in range(n):
        for j in range(n):
            x=int(i*tw*(1-ov)); y=int(j*th*(1-ov)); add(im[y:y+th,x:x+tw],x,y)
    b=torch.tensor(B,dtype=torch.float32).reshape(-1,4); s=torch.tensor(S)
    k=torchvision.ops.nms(b,s,0.4); return b[k].numpy(),s[k].numpy()
for c in (0.1,0.2,0.3,0.5):
    b,s=run(c); print('conf',c,'faces',len(b))
b,s=run(0.15); np.save('/tmp/spike/out/faces.npy',np.c_[b,s]); print('saved',len(b))
