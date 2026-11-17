import os
import numpy as np, cv2
im=cv2.imread(os.environ['PHOTO'])
d=np.load('/tmp/spike/out/boxes.npy')
for i,(x1,y1,x2,y2,s) in enumerate(d):
    c=(80,220,80) if s>=.4 else (0,200,255)
    cv2.rectangle(im,(int(x1),int(y1)),(int(x2),int(y2)),c,5)
im=cv2.resize(im,(2000,1126))
cv2.imwrite('/tmp/spike/out/annotated.jpg',im,[cv2.IMWRITE_JPEG_QUALITY,85])
