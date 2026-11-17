import os
import numpy as np, cv2, struct, zlib, os, io, json, time
from PIL import Image, ImageDraw, ImageFont
from reedsolo import RSCodec
SRC=os.environ.get('PHOTO')
OUT='/tmp/spike/out'; W,H=1280,720
CELL=8; COLS=13; STRIP_W=CELL*COLS; NSYM=40
DELTA=80.0; COEF=(1,1); T_DARK=125
URL=b'rsvp.withgoogle.com/events/android-developer-day-capetown'

# ---------- 1. blur ----------
def blur_heads(im, boxes, faces=()):
    """Gaussian blur on a soft ellipse around each estimated head (top of each person box)."""
    out=im.astype(np.float32); Hh,Ww=im.shape[:2]
    for x1,y1,x2,y2,s in boxes:
        w=x2-x1; h=y2-y1
        d=min(0.5*w,0.22*h)                    # estimated head diameter
        d=max(d,28)
        cx=(x1+x2)/2; cy=y1+0.55*d
        rx=0.75*d; ry=0.8*d
        sig=max(0.5*d,10)
        X1=int(max(cx-rx-3*sig,0));X2=int(min(cx+rx+3*sig,Ww));Y1=int(max(cy-ry-3*sig,0));Y2=int(min(cy+ry+3*sig,Hh))
        roi=im[Y1:Y2,X1:X2]
        if roi.size==0: continue
        bl=cv2.GaussianBlur(roi,(0,0),sig).astype(np.float32)
        m=np.zeros(roi.shape[:2],np.float32)
        cv2.ellipse(m,(int(cx-X1),int(cy-Y1)),(int(rx),int(ry)),0,0,360,1.0,-1)
        m=cv2.GaussianBlur(m,(0,0),max(sig*0.25,2))[...,None]
        out[Y1:Y2,X1:X2]=out[Y1:Y2,X1:X2]*(1-m)+bl*m
    # face pass: blur every detected face (padded) on top of the box-based head estimate
    for x1,y1,x2,y2,sc in faces:
        w=x2-x1; h=y2-y1; d=max(w,h,24)
        cx=(x1+x2)/2; cy=(y1+y2)/2; rx=0.85*d; ry=0.95*d; sig=max(0.45*d,8)
        X1=int(max(cx-rx-3*sig,0));X2=int(min(cx+rx+3*sig,Ww));Y1=int(max(cy-ry-3*sig,0));Y2=int(min(cy+ry+3*sig,Hh))
        src=np.clip(out[Y1:Y2,X1:X2],0,255).astype(np.uint8)
        if src.size==0: continue
        bl=cv2.GaussianBlur(src,(0,0),sig).astype(np.float32)
        m=np.zeros(src.shape[:2],np.float32)
        cv2.ellipse(m,(int(cx-X1),int(cy-Y1)),(int(rx),int(ry)),0,0,360,1.0,-1)
        m=cv2.GaussianBlur(m,(0,0),max(sig*0.25,2))[...,None]
        out[Y1:Y2,X1:X2]=out[Y1:Y2,X1:X2]*(1-m)+bl*m
    return np.clip(out,0,255).astype(np.uint8), None

# ---------- 2. payload ----------
def crc16(b):
    c=0xFFFF
    for x in b:
        c^=x<<8
        for _ in range(8): c=((c<<1)^0x1021)&0xFFFF if c&0x8000 else (c<<1)&0xFFFF
    return c
def make_payload(ts,tz15,lat,lon,hand,cam,photos):
    (hlo,hhi),(clo,cpt,chi)=hand,cam
    body=struct.pack('>B6sIbhhHHHHHB4sB',1,os.urandom(6),ts,tz15,lat,lon,hlo,hhi,clo,chi,cpt,photos,os.urandom(4),len(URL))+URL
    return body+struct.pack('>H',crc16(body))
def parse_payload(p):
    body,c=p[:-2],struct.unpack('>H',p[-2:])[0]
    if crc16(body)!=c: return None
    v,ev,ts,tz,lat,lon,hlo,hhi,clo,chi,cpt,ph,inst,ul=struct.unpack('>B6sIbhhHHHHHB4sB',body[:32])
    return dict(v=v,event=ev.hex(),ts=ts,tz15=tz,lat=lat/100.0,lon=lon/100.0,hand=[hlo,hhi],camera=[clo,chi],camera_point=cpt,photos=ph,url=body[32:32+ul].decode())
rsc=RSCodec(NSYM)
def to_bits(b): return np.unpackbits(np.frombuffer(bytes(b),np.uint8))
def to_bytes(bits): return np.packbits(bits.astype(np.uint8)).tobytes()

# ---------- 3. strip ----------
ROWS_TOTAL=H//CELL; FIND=2; DATA_ROWS=ROWS_TOTAL-2*FIND
def draw_strip(img, coded):
    bits=to_bits(coded); cap=DATA_ROWS*COLS; assert len(bits)<=cap,(len(bits),cap)
    full=np.zeros(cap,np.uint8); full[:len(bits)]=bits
    # pad with a pseudo-random fill so empty cells are not a flat block
    rng=np.random.default_rng(7); full[len(bits):]=rng.integers(0,2,cap-len(bits))
    g=full.reshape(DATA_ROWS,COLS)
    for r in range(ROWS_TOTAL):
        for c in range(COLS):
            if r<FIND or r>=ROWS_TOTAL-FIND: v=(r+c)%2
            else: v=g[r-FIND,c]
            img[r*CELL:(r+1)*CELL,c*CELL:(c+1)*CELL]=255 if v else 0
    return len(bits)
def read_strip(img,nbytes):
    g=cv2.cvtColor(img,cv2.COLOR_BGR2GRAY).astype(np.float32)
    bits=[]
    for r in range(FIND,ROWS_TOTAL-FIND):
        for c in range(COLS):
            cell=g[r*CELL+2:(r+1)*CELL-2,c*CELL+2:(c+1)*CELL-2]
            bits.append(1 if cell.mean()>127 else 0)
    return to_bytes(np.array(bits[:nbytes*8]))

# ---------- 4. watermark (QIM on one DCT coef, dark blocks, hashed bit index) ----------
def block_grid(y): return y.shape[0]//8, y.shape[1]//8
def bit_index(bx,by,n): return ((bx*73856093)^(by*19349663))%n
def embed_wm(img, coded):
    bits=to_bits(coded); n=len(bits)
    ycc=cv2.cvtColor(img,cv2.COLOR_BGR2YCrCb).astype(np.float32); y=ycc[...,0]
    bh,bw=block_grid(y); used=0
    for by in range(bh):
        for bx in range(STRIP_W//8,bw):
            blk=y[by*8:by*8+8,bx*8:bx*8+8]
            if blk.mean()>=T_DARK: continue
            d=cv2.dct(blk); b=bits[bit_index(bx,by,n)]
            off=b*DELTA/2; c=d[COEF]
            d[COEF]=np.round((c-off)/DELTA)*DELTA+off
            y[by*8:by*8+8,bx*8:bx*8+8]=cv2.idct(d); used+=1
    ycc[...,0]=np.clip(np.round(y),0,255)
    return cv2.cvtColor(ycc.astype(np.uint8),cv2.COLOR_YCrCb2BGR), used, bh*bw
def read_wm(img,n):
    y=cv2.cvtColor(img,cv2.COLOR_BGR2YCrCb)[...,0].astype(np.float32)
    bh,bw=block_grid(y); vote=np.zeros(n); cnt=np.zeros(n)
    for by in range(bh):
        for bx in range(STRIP_W//8,bw):
            blk=y[by*8:by*8+8,bx*8:bx*8+8]
            if blk.mean()>=T_DARK: continue
            c=cv2.dct(blk)[COEF]
            d0=abs(c-np.round(c/DELTA)*DELTA)
            d1=abs(c-(np.round((c-DELTA/2)/DELTA)*DELTA+DELTA/2))
            i=bit_index(bx,by,n); vote[i]+=(d0-d1)/(DELTA/2); cnt[i]+=1
    return (vote>0).astype(np.uint8), cnt

def try_decode(coded_bytes):
    try:
        dec=rsc.decode(coded_bytes)[0]; return parse_payload(bytes(dec))
    except Exception as e: return None

if __name__=='__main__':
    t0=time.time()
    src=cv2.imread(SRC); boxes=np.load(f'{OUT}/boxes.npy'); count=len(boxes)
    faces=np.load(f'{OUT}/faces.npy'); print('faces blurred',len(faces))
    blurred,mask=blur_heads(src,boxes,faces)
    small=cv2.resize(blurred,(W,H),interpolation=cv2.INTER_AREA)
    cv2.imwrite(f'{OUT}/blurred_only.jpg',small,[cv2.IMWRITE_JPEG_QUALITY,90])
    # overlay
    pil=Image.fromarray(cv2.cvtColor(small,cv2.COLOR_BGR2RGB)); d=ImageDraw.Draw(pil,'RGBA')
    f1=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf',26)
    f2=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf',19)
    rg=json.load(open(f'{OUT}/range.json')); cam=(rg['low'],rg['point'],rg['high']); hand=(55,65); ts=int(time.time()); tz15=8   # SAST = UTC+2
    lat,lon=-33.90,18.40
    gap=max(cam[0]-hand[1],hand[0]-cam[2],0); flag = gap>3
    d.rectangle([STRIP_W+10,10,W-10,112],fill=(0,0,0,150))
    d.text((STRIP_W+22,16),'ANDROID DEVELOPER DAY CAPE TOWN',font=f1,fill=(233,241,236))
    d.text((STRIP_W+22,52),'17 NOV 2026 10:42 SAST   33.90S 18.40E',font=f2,fill=(159,217,191))
    d.text((STRIP_W+22,80),f'HAND {hand[0]}-{hand[1]}  CAMERA {cam[0]}-{cam[2]}  {"CHECK COUNTS" if flag else "RANGES OVERLAP"}',font=f2,fill=(240,160,102) if flag else (159,217,191))
    d.rectangle([STRIP_W,H-40,W,H],fill=(0,0,0,185))
    d.text((STRIP_W+14,H-32),'github.com/coderlevelup/clubcheck',font=f2,fill=(233,241,236))
    comp=cv2.cvtColor(np.array(pil),cv2.COLOR_RGB2BGR)
    payload=make_payload(ts,tz15,round(lat*100),round(lon*100),hand,cam,1)
    coded=bytes(rsc.encode(payload)); print('payload',len(payload),'coded',len(coded),'bits',len(coded)*8)
    nb=draw_strip(comp,coded)
    final,used,tot=embed_wm(comp,coded)
    print('watermark blocks used',used,'of',tot,'=> per bit',used/(len(coded)*8))
    p=cv2.PSNR(comp,final); print('watermark PSNR vs unmarked',round(p,1),'dB')
    cv2.imwrite(f'{OUT}/shared.png',final)
    json.dump(dict(camera=cam,hand=hand,flag=bool(flag),payload=parse_payload(payload),coded_len=len(coded),wm_blocks=used,blocks_total=tot),open(f'{OUT}/meta.json','w'),indent=1)

    # ---------- stress tests ----------
    n=len(coded)
    def decode_both(im,label):
        if im.shape[:2]!=(H,W): im=cv2.resize(im,(W,H),interpolation=cv2.INTER_CUBIC)
        s=try_decode(read_strip(im,n))
        bits,cnt=read_wm(im,n*8); w=try_decode(to_bytes(bits))
        raw_ber=np.mean(bits!=to_bits(coded))
        print(f'{label:34s} strip={"OK " if s else "FAIL"} watermark={"OK " if w else "FAIL"} (wm raw bit err {raw_ber:.1%})')
        return bool(s),bool(w)
    def jpeg(im,q):
        ok,buf=cv2.imencode('.jpg',im,[cv2.IMWRITE_JPEG_QUALITY,q]); return cv2.imdecode(buf,1),len(buf)
    decode_both(final,'PNG, untouched')
    for q in (95,85,75,65,50):
        im,sz=jpeg(final,q); decode_both(im,f'JPEG q{q} ({sz//1024} KB)')
    for sc in (0.9,0.8,0.6):
        sm2=cv2.resize(final,(int(W*sc),int(H*sc)),interpolation=cv2.INTER_AREA)
        im,sz=jpeg(sm2,75); decode_both(im,f'resize {int(sc*100)}% + JPEG q75')
    # crop strip off (watermark only): black out strip columns
    im,_=jpeg(final,75); im2=im.copy(); im2[:,:STRIP_W]=0
    decode_both(im2,'strip removed + JPEG q75')
    print('secs',round(time.time()-t0,1))
