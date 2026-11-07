from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont
import math,time
root=Path(__file__).resolve().parent.parent
colors={'DEEPSLATE_BRICKS':(68,72,82),'DEEPSLATE_TILES':(51,55,64),'CRACKED_DEEPSLATE_BRICKS':(56,61,72),'POLISHED_DEEPSLATE':(75,79,88),'POLISHED_BLACKSTONE_BRICKS':(53,47,62),'POLISHED_BLACKSTONE':(45,39,53),'CHISELED_POLISHED_BLACKSTONE':(78,68,86),'CHISELED_DEEPSLATE':(74,79,86),'OBSIDIAN':(28,24,42),'CRYING_OBSIDIAN':(69,40,104),'CALCITE':(187,181,165),'AMETHYST_BLOCK':(118,79,161),'GILDED_BLACKSTONE':(108,85,55),'BLACK_GLAZED_TERRACOTTA':(29,30,44),'BLACK_CARPET':(30,28,36),'POLISHED_BASALT':(76,77,83),'BOOKSHELF':(104,75,62),'DARK_OAK_PLANKS':(62,43,36),'SEA_LANTERN':(158,206,201),'SHROOMLIGHT':(231,161,87),'IRON_CHAIN':(86,85,96),'SOUL_LANTERN':(72,199,204),'PURPLE_CANDLE':(125,76,150),'AMETHYST_CLUSTER':(158,111,209),'POLISHED_BLACKSTONE_WALL':(66,57,77),'POLISHED_BLACKSTONE_BRICK_WALL':(66,57,77),'POLISHED_BLACKSTONE_BRICK_STAIRS':(63,55,73),'POLISHED_BLACKSTONE_BRICK_SLAB':(63,55,73),'PURPLE_STAINED_GLASS':(83,41,128)}
lo=np.array([-50,78,-75]);size=np.array([101,58,151]);grid=np.zeros(tuple(size),dtype=np.int16)
ids={};names=['AIR'];palette=[(0,0,0)]
for row in (root/'build/cathedral.tsv').read_text().splitlines():
 x,y,z,m=row.split('\t');pos=np.array([int(x),int(y),int(z)])-lo
 name=m.split(':')[-1].split('[')[0].upper()
 if name=='AIR':continue
 if name not in ids:ids[name]=len(names);names.append(name);palette.append(colors.get(name,(75,67,87)))
 grid[tuple(pos)]=ids[name]
width,height=1200,760
origin=np.array([.5,89.5,-51.5]);target=np.array([.5,97,24]);forward=target-origin;forward/=np.linalg.norm(forward)
right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,forward)
u,v=np.meshgrid((np.arange(width)+.5-width/2)/(width/2),-(np.arange(height)+.5-height/2)/(width/2))
direction=forward+u.reshape(-1,1)*right*math.tan(math.radians(43))+v.reshape(-1,1)*up*math.tan(math.radians(43))
direction/=np.linalg.norm(direction,axis=1)[:,None]
N=len(direction);start=origin-lo;cell=np.tile(np.floor(start).astype(int),(N,1));step=np.sign(direction).astype(int)
delta=np.abs(1/direction);maximum=((cell+(step>0)-start)/direction)
active=np.arange(N);hit=np.zeros(N,dtype=int);distance=np.zeros(N);normal=np.zeros((N,3));points=np.zeros((N,3));axes=np.zeros(N,dtype=int)
for iteration in range(330):
 if not len(active):break
 axis=np.argmin(maximum[active],axis=1);advance=maximum[active,axis]
 cell[active,axis]+=step[active,axis];maximum[active,axis]+=delta[active,axis]
 inside=np.all((cell[active]>=0)&(cell[active]<size),axis=1)
 active=active[inside];axis=axis[inside];advance=advance[inside]
 block=grid[tuple(cell[active].T)];solid=block!=0;selected=active[solid]
 hit[selected]=block[solid];distance[selected]=advance[solid];normal[selected,axis[solid]]=-step[selected,axis[solid]]
 points[selected]=origin+advance[solid,None]*direction[selected];axes[selected]=axis[solid]
 active=active[~solid]
print('rendered',iteration,'steps; misses',len(active))
pal=np.array(palette)/255
rgb=pal[hit];face=np.abs(normal[:,0])*.80+np.abs(normal[:,1])*1.03+np.abs(normal[:,2])*.90
noise=np.sin(points[:,0]*13.17+points[:,1]*39.13+points[:,2]*7.89)*.045
rgb*=np.clip(face[:,None]+noise[:,None],.45,1.15)
# Fine mortar/joint edges communicate voxel scale without claiming game textures.
frac=points-np.floor(points)
for ax in range(3):
 mask=(axes==ax)&(hit!=0)
 coords=[a for a in range(3) if a!=ax]
 edge=np.minimum.reduce([frac[:,coords[0]],1-frac[:,coords[0]],frac[:,coords[1]],1-frac[:,coords[1]]])
 rgb[mask&(edge<.025)]*=.62
# Restrained violet/amber lighting near the focal sculpture.
focus=np.linalg.norm(points-np.array([0,101,24]),axis=1)
rgb+=np.exp(-focus[:,None]/15)*np.array([.06,.01,.10])
fog=np.exp(-distance/250)[:,None];rgb=rgb*fog+np.array([.035,.027,.060])*(1-fog)
for name in ['SEA_LANTERN','SOUL_LANTERN','SHROOMLIGHT','PURPLE_CANDLE']:
 if name in ids:
  mask=hit==ids[name];rgb[mask]=np.minimum(1,pal[hit[mask]]*1.25+.06)
image=Image.fromarray((np.clip(rgb,0,1).reshape(height,width,3)*255).astype('uint8'))
canvas=Image.new('RGB',(width,height+120),'#0c0b13');canvas.paste(image,(0,76));draw=ImageDraw.Draw(canvas)
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
draw.text((30,16),'CATEDRAL DEL ECLIPSE  ·  81 × 129 bloques',font=ImageFont.truetype(font,26),fill='#e2d5ef')
draw.text((30,50),'Geometría del generador 0.2 · una gran sala cerrada',font=ImageFont.truetype(font,14),fill='#a89bb7')
draw.text((30,height+88),'Vista técnica: colores, iluminación y formas de bloques aproximados. No es una captura de Minecraft.',font=ImageFont.truetype(font,14),fill='#a89bb7')
canvas.save(root/'build/cathedral-preview.png')
