import os
import sys, json
from ultralytics import YOLO
img = os.environ['PHOTO']
for name in ['yolov8n','yolov8s']:
    m = YOLO(f'/tmp/spike/models/{name}.pt')
    for imgsz in (1280, 1920):
        for conf in (0.15, 0.25, 0.4):
            r = m.predict(img, imgsz=imgsz, conf=conf, classes=[0], verbose=False)[0]
            print(name, imgsz, conf, len(r.boxes))
