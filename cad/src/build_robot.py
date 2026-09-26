#!/usr/bin/env python3
"""UBOR-JAVA R01 conceptual solid CAD; mm. Requires CadQuery 2.6+.
X forward, Y left, Z upward. This is not a manufacturing release.
All STEP / SVG / meshes derive from these same solids.
"""
from pathlib import Path
import math, json, csv
import cadquery as cq
from cadquery import exporters
import ezdxf

ROOT=Path(__file__).resolve().parents[2]
CAD=ROOT/'cad'
for p in ('step','parts','drawings','render'): (CAD/p).mkdir(exist_ok=True)
P=dict(frame_length=660,frame_width=430,frame_z=160,tube=20,wall=2,
       wheel_diameter=200,wheel_width=45,track=500,drive_x=-190,
       conveyor_width=320,roller_radius=25,roller_low=[300,0,85],
       roller_high=[-30,0,445],brush_x=383,brush_z=62,brush_radius=57,
       bin_length=320,bin_width=360,bin_height=220,bin_wall=3,bin_center=[-175,0,290],
       gross_mass_design_kg=30,max_payload_kg=4,max_speed_mps=.30)
(ROOT/'cad'/'parameters.json').write_text(json.dumps(P,indent=2),encoding='utf-8')
parts=[]
COL={'al':(.73,.78,.81),'steel':(.38,.42,.46),'rubber':(.12,.14,.16),'plastic':(.12,.43,.47),'guard':(.87,.60,.13),'pcb':(.15,.44,.23),'battery':(.23,.27,.33)}
def add(name,shape,mat='al',note='',mass_override=None):
    if isinstance(shape,cq.Workplane): shape=shape.val()
    assert shape.isValid(),name
    parts.append(dict(name=name,shape=shape,mat=mat,note=note,mass_override=mass_override))
    return shape

def box(a,b,c,x,y,z): return cq.Workplane('XY').box(a,b,c).translate((x,y,z))
def cy(r,length,x,y,z): return cq.Workplane('XZ').circle(r).extrude(length/2,both=True).translate((x,y,z))
def cz(r,length,x,y,z): return cq.Workplane('XY').circle(r).extrude(length/2,both=True).translate((x,y,z))
def tube_x(length,x,y,z):
    return box(length,20,20,x,y,z).cut(box(length+2,16,16,x,y,z))
def tube_y(length,x,y,z):
    return box(20,length,20,x,y,z).cut(box(16,length+2,16,x,y,z))
# Bolted chassis, rear and front cross members do not block the belt path.
for side in (-1,1): add(f'frame_side_{side:+d}',tube_x(660,-20,side*205,160))
for x in (-340,-15,270): add(f'cross_member_{x}',tube_y(390,x,0,160))
# Bin-only support plate, deliberately no floor in the conveyor sweep.
deck=box(310,380,3,-180,0,174.5)
for x in (-310,-50):
    for y in (-150,150): deck=deck.cut(cz(3.3,8,x,y,174.5))
add('bin_support_deck',deck)
for x in (-315,-40,250):
    for y in (-195,195):
        gus=box(50,50,3,x,y,171.5)
        for xx,yy in ((-16,-16),(16,16)):
            gus=gus.cut(cz(3.3,10,x+xx,y+yy,171.5))
        add(f'gusset_{x}_{y}',gus)
# Independently driven wheels, shafts and placeholder purchased motor envelopes.
for side in (-1,1):
    yy=side*250
    tyre=cy(100,45,-190,yy,100).cut(cy(64,50,-190,yy,100))
    hub=cy(63,39,-190,yy,100).cut(cy(6,50,-190,yy,100))
    add(f'tyre_{side:+d}',tyre,'rubber',mass_override=.65)
    add(f'hub_{side:+d}',hub,'al',mass_override=.35)
    add(f'wheel_shaft_{side:+d}',cy(6,85,-190,side*224,100),'steel')
    bracket=box(62,6,90,-190,side*209,125).cut(cy(14,14,-190,side*209,100))
    for x in (-210,-170): bracket=bracket.cut(cy(3.3,14,x,side*209,151))
    add(f'drive_mount_{side:+d}',bracket,'al','motor interface must follow selected gearmotor drawing')
    add(f'drive_motor_envelope_{side:+d}',cy(22,90,-190,side*150,100),'steel','24 V, 30 rpm loaded, >=4 N m continuous; purchase interface not frozen',.9)
    # Outboard support; hub load should not be supported by a small motor shaft alone.
    bearing=box(44,16,44,-190,side*221,100).cut(cy(6,20,-190,side*221,100))
    add(f'wheel_bearing_block_{side:+d}',bearing,'steel',mass_override=.12)
# Front caster wheel and fork envelopes, mounting holes.
for side in (-1,1):
    x,y=220,side*195
    add(f'caster_tyre_{side:+d}',cy(37.5,25,x,y,37.5),'rubber',mass_override=.18)
    for sy in (-1,1): add(f'caster_fork_{side:+d}_{sy:+d}',box(45,4,90,x,y+sy*18,78),'steel')
    add(f'caster_pivot_{side:+d}',cz(10,30,x,y,136),'steel')
    add(f'caster_mount_{side:+d}',box(65,60,4,x,y,147),'al')
# Removable open bin, 24.1 L geometric internal volume, no sorting compartments.
for side in (-1,1): add(f'bin_slide_{side:+d}',box(310,20,4,-180,side*145,178),'plastic')
outer=box(320,360,220,-175,0,290)
inner=box(314,354,220,-175,0,293)
binshape=outer.cut(inner)
add('removable_bin',binshape,'plastic','HDPE concept; service removal to rear',1.6)
for side in (-1,1):
    add(f'bin_handle_{side:+d}',box(80,14,20,-175,side*186,370),'plastic')
# Battery mounted low, distinct envelope, protected from the conveyor.
add('battery_8S_LFP_envelope',box(230,140,80,-150,0,120),'battery','25.6 V 10 Ah pack with BMS; mounting envelope only',2.8)
for side in (-1,1): add(f'battery_strap_{side:+d}',box(18,170,3,-150+side*75,0,161),'steel')
# Low battery tray and suspension angles; procurement-specific bolt patterns remain open.
add('battery_tray',box(260,390,3,-150,0,73.5),'al')
for xx in (-265,-35):
    for side in (-1,1):
        add(f'battery_hanger_{xx}_{side:+d}',box(20,5,74,xx,side*187.5,111.5),'al')
        add(f'battery_hanger_flange_{xx}_{side:+d}',box(40,30,3,xx,side*195,148.5),'al')
for xx in (-225,-75):
    add(f'battery_pad_{xx}',box(30,140,5,xx,0,77.5),'rubber')
    for side in (-1,1): add(f'battery_strap_leg_{xx}_{side:+d}',box(18,1.5,85,xx,side*85,117.5),'steel')
# Conveyor capsule cross-section: real closed endless belt, not a filled solid block.
a=cq.Vector(300,0,85); b=cq.Vector(-30,0,445)
v=b-a; L=v.Length; angle=math.degrees(math.atan2(v.z,v.x)); mid=(a+b).multiply(.5)
u=v.normalized(); n=cq.Vector(u.z,0,-u.x) # normal above the upper belt run

def capsule(radius,width):
    return cq.Workplane('XZ').center(mid.x,mid.z).slot2D(L+2*radius,2*radius,angle).extrude(width/2,both=True)
belt=capsule(28,320).cut(capsule(25,330))
add('endless_belt',belt,'rubber','closed belt centreline Lc=2*C+2*pi*26.5; cleat bonding not detailed',1.2)
for tag,p in [('lower',a),('upper',b)]:
    roller=cy(25,318,p.x,0,p.z).cut(cy(23,310,p.x,0,p.z)).cut(cy(6,330,p.x,0,p.z))
    add(tag+'_roller',roller,'al')
    add(tag+'_shaft',cy(6,392,p.x,0,p.z),'steel')
    for side in (-1,1):
        block=box(40,14,40,p.x,side*186,p.z).cut(cy(6,22,p.x,side*186,p.z))
        add(f'{tag}_bearing_{side:+d}',block,'steel',mass_override=.12)
# Cleats on upper and lower straight runs; excludes curved ends by design.
for k in range(1,5):
    for upper in (True,False):
        nn=n if upper else n.multiply(-1)
        pos=a+u.multiply(k*L/5)+nn.multiply(40)
        cleat=box(5,312,24,0,0,0).rotate((0,0,0),(0,1,0),-angle).translate(pos.toTuple())
        add(f'cleat_{k}_{int(upper)}',cleat,'rubber','flexible TPU; swept interference not certified')
# Side plates following belt with guard volume above it.
pts=[]
for p,nn in [(a-u.multiply(35),-40),(b+u.multiply(35),-40),(b+u.multiply(35),100),(a-u.multiply(35),100)]:
    q=p+n.multiply(nn); pts.append((q.x,q.z))
for side in (-1,1):
    panel=cq.Workplane('XZ').polyline(pts).close().extrude(3/2,both=True).translate((0,side*175,0))
    for p in (a,b): panel=panel.cut(cy(7,12,p.x,side*175,p.z))
    add(f'conveyor_side_guard_{side:+d}',panel,'guard')
# Upper guarded roof follows the belt and leaves pickup / discharge openings.
roof=box(L-35,352,2,0,0,0).rotate((0,0,0),(0,1,0),-angle).translate((mid+n.multiply(103)).toTuple())
add('conveyor_roof',roof,'guard','fixed interlocked guard; opening sizes need risk assessment')
# Supports beside conveyor.
for side in (-1,1):
    add(f'upper_support_{side:+d}',box(20,20,270,-25,side*205,305).cut(box(16,16,272,-25,side*205,305)),'al')
    add(f'lower_support_{side:+d}',box(20,20,80,285,side*205,125),'al')
add('conveyor_motor_envelope',cy(20,65,-30,-235,445),'steel','24 V geared motor 45-60 rpm; guarded coupling',.45)
add('conveyor_coupling',cy(12,32,-30,-199,445),'steel')
# Soft front brush plus a scoop. Soft fin / lip engagement is intentional.
add('brush_core',cy(20,320,383,0,62),'plastic')
add('brush_shaft',cy(6,402,383,0,62),'steel')
for k in range(8):
    ang=k*45
    fin=box(36,318,3,38,0,0).rotate((0,0,0),(0,1,0),ang).translate((383,0,62))
    add(f'brush_fin_{k}',fin,'rubber','flexible TPU, nominal 57 mm envelope')
for side in (-1,1):
    support=box(85,4,115,350,side*179,111).cut(cy(6,12,383,side*179,62))
    add(f'brush_support_{side:+d}',support,'al')
    add(f'brush_bearing_{side:+d}',cy(14,8,383,side*184,62).cut(cy(6,14,383,side*184,62)),'steel')
add('brush_motor_envelope',cy(20,60,383,-228,62),'steel','24 V 120 rpm; mounting envelope',.4)
add('brush_coupling',cy(12,25,383,-194,62),'steel')
lip=cq.Workplane('XZ').polyline([(385,4),(293,65),(293,68),(385,7)]).close().extrude(320/2,both=True)
add('flexible_pickup_lip',lip,'rubber','replaceable TPU, first contact at floor; adjust during commissioning')
# Camera goal is a UVC camera: no implicit libcamera/OpenCV compatibility claim.
for side in (-1,1): add(f'camera_mast_{side:+d}',box(20,20,450,250,side*205,390).cut(box(16,16,452,250,side*205,390)),'al')
add('camera_crossbar',tube_y(430,250,0,615))
add('UVC_camera_envelope',box(68,95,40,255,0,645),'plastic','position / pitch calibrated, not an optical model',.12)
add('front_range_sensor',box(35,70,30,285,0,580),'plastic','sensor volume; sight line must avoid guard',.05)
# Electronics enclosure at the rear, accessible without reaching a nip.
for side in (-1,1):
    add(f'electronics_post_{side:+d}',box(20,20,260,-280,side*213,300).cut(box(16,16,262,-280,side*213,300)))
add('electronics_support_crossbar',tube_y(426,-280,0,430))
encl=box(270,140,95,-185,0,487.5).cut(box(264,134,92,-185,0,490.5))
add('electronics_enclosure',encl,'plastic','separate from trash volume; ventilation and sealing not rated')
add('electronics_lid',box(270,140,3,-185,0,536.5),'plastic')
add('pi5_board_envelope',box(85,56,1.6,-220,0,458.5),'pcb','purchased PCB envelope',.05)
add('power_interconnect_envelope',box(140,85,1.6,-135,0,468.5),'pcb','module mounting area; not routed PCB',.3)
add('pi_cooler_envelope',box(60,45,23,-220,0,474.5),'al',mass_override=.07)
# Bumper brackets are connected to the front cross member.
add('bumper_crossbar',tube_y(490,270,0,145))
for side in (-1,1): add(f'bumper_arm_{side:+d}',tube_x(152,346,side*225,145))
# Bumper bars and conspicuous mushroom emergency stop.
add('rear_bumper',box(25,450,30,-358,0,130),'rubber')
add('front_bumper_left',box(25,90,30,422,side*225,145),'rubber') if False else None
for side in (-1,1): add(f'front_bumper_{side:+d}',box(25,70,30,422,side*225,145),'rubber')
add('estop_stem',cz(11,20,-280,55,548),'steel')
add('estop_mushroom',cz(22,15,-280,55,565.5),'guard','electrical normally-closed dual contact')
# Export individual parts and named assembly, with removable items for a service view.
assembly=cq.Assembly(name='UBOR_JAVA_R01')
service=cq.Assembly(name='UBOR_JAVA_R01_SERVICE')
densities={'al':2.7e-6,'steel':7.85e-6,'rubber':1.1e-6,'plastic':.95e-6,'guard':2.7e-6,'pcb':1.8e-6,'battery':1.0e-6}
rows=[]
for p in parts:
    name,s=p['name'],p['shape']
    exporters.export(s,str(CAD/'parts'/f'{name}.step'))
    assembly.add(s,name=name,color=cq.Color(*COL[p['mat']]))
    loc=cq.Location()
    if name=='removable_bin' or name.startswith('bin_handle'): loc=cq.Location(cq.Vector(-420,0,0))
    if name in ('conveyor_roof','electronics_lid'): loc=cq.Location(cq.Vector(0,0,200))
    service.add(s,name=name,color=cq.Color(*COL[p['mat']]),loc=loc)
    bb=s.BoundingBox()
    rows.append(dict(name=name,material=p['mat'],volume_mm3=round(s.Volume(),3),
          geometry_mass_kg=round(s.Volume()*densities[p['mat']],4),
          estimated_mass_kg=round(p['mass_override'] if p['mass_override'] is not None else s.Volume()*densities[p['mat']],4),
          x_mm=round(bb.xlen,2),y_mm=round(bb.ylen,2),z_mm=round(bb.zlen,2),note=p['note']))
assembly.save(str(CAD/'step'/'UBOR_JAVA_R01_assembly.step'))
service.save(str(CAD/'step'/'UBOR_JAVA_R01_service.step'))
compound=cq.Compound.makeCompound([p['shape'] for p in parts])
exporters.export(compound,str(CAD/'step'/'UBOR_JAVA_R01.stl'),tolerance=.8,angularTolerance=.15)
# Parts/meshes metadata is also the authoritative input of the off-screen renderer.
meshmeta=[]
for p in parts:
    verts,tri=p['shape'].tessellate(1.0,.2)
    meshmeta.append({'name':p['name'],'vertices':[v.toTuple() for v in verts],
                     'triangles':tri,'color':COL[p['mat']]})
(CAD/'render'/'mesh_scene.json').write_text(json.dumps(meshmeta),encoding='utf-8')
# Hidden-line drawings are generated from exact CAD topology.
from export_views import export_views
export_views(compound,CAD/'drawings')
# Manufacturable flat concept parts as DXF, unit mm, no false bend deductions.
def dxf_rect(name,w,h,holes):
    d=ezdxf.new('R2010'); d.units=4; m=d.modelspace()
    m.add_lwpolyline([(0,0),(w,0),(w,h),(0,h)],close=True)
    for x,y,r in holes: m.add_circle((x,y),r)
    d.saveas(CAD/'drawings'/f'{name}.dxf')
dxf_rect('bin_support_deck_cut_310x380',310,380,[(25,40,3.3),(285,40,3.3),(25,340,3.3),(285,340,3.3)])
dxf_rect('gusset_cut_50x50',50,50,[(9,9,3.3),(41,41,3.3)])
# Engineering calculations: assumptions are written alongside the results.
m=30; g=9.81; slope=math.radians(5); crr=.03; acc=.25; r=.1
force=m*g*(math.sin(slope)+crr*math.cos(slope))+m*acc
I=(20**4-16**4)/12; beamL=660; F=m*g/2; E=69000
bb=compound.BoundingBox()
checks=dict(status='CONCEPT_NOT_MANUFACTURING_RELEASE',part_count=len(parts),all_solids_valid=all(p['shape'].isValid() for p in parts),
   total_solids=sum(len(p['shape'].Solids()) for p in parts),cad_bbox_mm=[round(bb.xlen,2),round(bb.ylen,2),round(bb.zlen,2)],
   modeled_mass_estimate_kg=round(sum(z['estimated_mass_kg'] for z in rows),2),gross_mass_design_kg=30,
   bin_internal_liters=round(314*354*217/1e6,2),belt_center_distance_mm=L,belt_pitch_length_mm=2*L+2*math.pi*26.5,
   belt_angle_degrees=180-angle,tractive_force_N=force,required_wheel_torque_Nm_with_margin_1_5=force*r/2*1.5,
   wheel_rpm_at_0_3_mps=.3/(2*math.pi*r)*60,beam_I_mm4=I,beam_sigma_MPa=F*beamL/4*10/I,
   beam_deflection_mm=F*beamL**3/(48*E*I),nominal_energy_Wh=25.6*10,
   runtime_assuming_80W_h=25.6*10*.8*.9/80,
   stop_distance_assumption_m=.3*.3+.3**2/(2*.6)+.10,
   assumptions=['30 kg gross design load; real weigh-in required','rolling coefficient 0.03 and 5 deg slope are assumptions',
   'simple beam calculation, not FEA, fatigue or joint validation','battery/load mass distribution must be measured',
   'flexible brush fins contact pickup lip by intention','bearings/shafts and cleats have idealized fit; fasteners, tension adjustment and all guards need detailing',
   'no blanket collision-free claim; assembly contacts and soft interference are not evaluated as rigid failures'])
(ROOT/'verification'/'cad_checks.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')
with (CAD/'mechanical_bom.csv').open('w',encoding='utf-8-sig',newline='') as f:
    w=csv.DictWriter(f,fieldnames=rows[0].keys()); w.writeheader(); w.writerows(rows)
print(json.dumps(checks,indent=2))
