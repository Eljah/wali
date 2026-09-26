#!/usr/bin/env python3
"""Native hierarchical KiCad schematic plus canonical connection graph.
No KiCad ERC / PCB routing claim. This script is an ECAD authoring tool, not robot firmware.
"""
from pathlib import Path
import uuid,json,csv,math,html,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];DEST=ROOT/'electronics/kicad';DEST.mkdir(exist_ok=True)
uid=lambda:str(uuid.uuid4())
ROOTID=uid();PROJECT='UBOR_Electronics';sheets=[];components=[]
def sheet(name,title):
 d={'name':name,'title':title,'uuid':uid(),'id':uid(),'components':[]};sheets.append(d);return d
# pins: number, name, net. Symbols are self-contained, with actual IC pins where specified.
def add(s,ref,value,pins,note=''):
 d={'ref':ref,'value':value,'pins':[{'number':str(n),'name':nm,'net':net}for n,nm,net in pins],'note':note,'uuid':uid(),'sheet':s['name']};s['components'].append(d);components.append(d);return d
def two(s,ref,value,a,b,note=''):return add(s,ref,value,[(1,'1',a),(2,'2',b)],note)
power=sheet('01_power','Power / battery / precharge / motor contactor')
add(power,'BT1','8S LiFePO4 / BMS / 25.6V 10Ah',[(1,'PACK+','BAT_RAW'),(2,'PACK-','GND')],'Assembled protected pack; charger external; no cell assembly instructions')
two(power,'F1','15A DC fuse / near pack','BAT_RAW','BAT_FUSED')
two(power,'S0','Main DC disconnect','BAT_FUSED','BAT_SW')
two(power,'F2','3A logic branch','BAT_SW','BUCK_IN')
add(power,'PS1','Regulated 5.1V / 5A converter',[(1,'IN+','BUCK_IN'),(2,'IN-','GND'),(3,'OUT+','V5'),(4,'OUT-','GND')],'Input >=36V; Pi5 compatible USB-C power/advertisement; verify undervoltage')
add(power,'JUSB1','Pi5 USB-C power interface',[(1,'V5','V5'),(2,'GND','GND')],'Abstract power connector, NOT a USB-C receptacle footprint or CC wiring')
add(power,'K1','24V DC contactor + isolated feedback',[(1,'MAIN_IN','BAT_SW'),(2,'MAIN_OUT','VM_BUS'),(3,'COIL+','COIL_PLUS'),(4,'COIL-','COIL_LOW'),(5,'AUX_NO','K1_SENSE'),(6,'AUX_COM','GND')],'Select >=30V DC breaking rating >=15A; auxiliary contact required; pin numbers are interface labels')
two(power,'S1B','E-STOP second NC pole','BAT_SW','COIL_PLUS')
add(power,'Q1','FQP30N06L / TO-220',[(1,'G','K1_GATE'),(2,'D','COIL_LOW'),(3,'S','GND')],'Driven by 5V AHCT buffer, not directly by 3.3V GPIO')
add(power,'D1','1N4007 coil diode',[(1,'K','COIL_PLUS'),(2,'A','COIL_LOW')],'Measure added release delay; cathode at coil+')
two(power,'RG1','100R gate','K1_GATE_DRIVE','K1_GATE');two(power,'RGS1','100k gate pulldown','K1_GATE','GND')
two(power,'CBULK','10000uF / 50V low-ESR','VM_BUS','GND','Bus energy buffer, NOT validated regenerative clamp')
two(power,'RBLEED','1k / 2W','VM_BUS','GND','Discharge time is tens of seconds; measure voltage before service')
two(power,'SPRE','Momentary PRECHARGE only','BAT_SW','PRE_R')
two(power,'RPRE','22R / pulse >=5J','PRE_R','VM_BUS','Hold >=1.2 s before physical ARM; release after ARM. Validate inrush and contacts.')
two(power,'RVTOP','100k / 0.1%','BAT_SW','ADC_BAT');two(power,'RVBOT','10k / 0.1%','ADC_BAT','GND');two(power,'CV','10nF','ADC_BAT','GND')
watch=sheet('02_watchdog','Hardware watchdog and edge-triggered manual rearm')
add(watch,'UWD','TPS3431 DRB',[(1,'VDD','V3V3'),(2,'CWD','CWD_SET'),(3,'EN','V3V3'),(4,'GND','GND'),(5,'SET1','V3V3'),(6,'WDI','HEARTBEAT'),(7,'nWDO','WD_OK'),(8,'ENOUT','WD_OK'),(9,'PAD','GND')],'CWD pull-up 10k: timeout nominal200ms,170..230ms; ENOUT tied to WDO')
two(watch,'RCWD','10k','V3V3','CWD_SET');two(watch,'RWDO','10k','V3V3','WD_OK');two(watch,'RWD_PD','100k','WD_OK','GND')
two(watch,'S1A','E-STOP NC auxiliary','WD_OK','SAFE_1');two(watch,'S3A','Guard NC','SAFE_1','SAFE_2');two(watch,'S4A','Bumper NC','SAFE_2','SAFE_3');two(watch,'S5A','Cliff relay NC','SAFE_3','CLEAR_N')
two(watch,'RCLR','100k','CLEAR_N','GND')
add(watch,'UFF','SN74LVC1G74 DCT/DCU',[(1,'CLK','ARM_EDGE'),(2,'D','V3V3'),(3,'nQ',None),(4,'GND','GND'),(5,'Q','HW_ENABLE'),(6,'nCLR','CLEAR_N'),(7,'nPRE','V3V3'),(8,'VCC','V3V3')],'A held ARM button cannot rearm after a watchdog clear; power-up reset supplied by ENOUT')
two(watch,'SARM','Physical ARM NO','V3V3','ARM_BUTTON');two(watch,'RARM','10k','ARM_BUTTON','ARM_RC');two(watch,'RARM_PD','100k','ARM_RC','GND');two(watch,'CARM','100nF','ARM_RC','GND')
add(watch,'USCH','SN74LVC1G17',[(1,'NC',None),(2,'A','ARM_RC'),(3,'GND','GND'),(4,'Y','ARM_EDGE'),(5,'VCC','V3V3')])
add(watch,'UAND','SN74LVC1G08',[(1,'A','SOFT_ARM'),(2,'B','HW_ENABLE'),(3,'GND','GND'),(4,'Y','RUN_EN'),(5,'VCC','V3V3')])
add(watch,'UINV','SN74LVC1G04',[(1,'NC',None),(2,'A','RUN_EN'),(3,'GND','GND'),(4,'Y','PWM_OE'),(5,'VCC','V3V3')])
add(watch,'UGATE','SN74AHCT1G125',[(1,'nOE','GND'),(2,'A','HW_ENABLE'),(3,'GND','GND'),(4,'Y','K1_GATE_DRIVE'),(5,'VCC','V5')],'TTL input accepts 3.3V; 5V gate drive makes FQP30N06L Rds specification applicable')
for ref,net,val in [('RSA','SOFT_ARM','10k'),('RHB','HEARTBEAT','10k'),('RSLEEP','RUN_EN','10k'),('RHW','HW_ENABLE','100k')]:two(watch,ref,val,net,'GND')
for ref,net in [('CWD','V3V3'),('CFF','V3V3'),('CSCH','V3V3'),('CAND','V3V3'),('CINV','V3V3'),('CGATE','V5')]:two(watch,ref,'100nF at IC',net,'GND')
io=sheet('03_io','Raspberry Pi header and byte buses / 3.3V logic only')
bcm={2:3,3:5,17:11,18:12,27:13,22:15,23:16,24:18,10:19,9:21,25:22,11:23,8:24,7:26,5:29,6:31,12:32,13:33,19:35,16:36,26:37,20:38,21:40}
nets={2:'I2C_SDA',3:'I2C_SCL',17:'SPI1_CS1',18:'SPI1_CS0',27:'SOFT_ARM',22:'HEARTBEAT',23:'ESTOP_SENSE',24:'BUMPER_SENSE',10:'SPI0_MOSI',9:'SPI0_MISO',25:'CLIFF_SENSE',11:'SPI0_SCLK',8:'SPI0_CS0',7:'SPI0_CS1',5:'GUARD_SENSE',6:'FULL_SENSE',12:'INTAKE_BEAM',13:'DROP_BEAM',19:'SPI1_MISO',16:'DRV_FAULT_N',26:'K1_SENSE',20:'SPI1_MOSI',21:'SPI1_SCLK'}
pi=[(1,'3V3','V3V3'),(6,'GND','GND')]+[(pin,'BCM'+str(b),nets[b])for b,pin in bcm.items()]
add(io,'JPI','Raspberry Pi5 40-pin header',pi,'Number is physical header pin; BCM is in pin name. Omitted pins not connected; power arrives via USB-C.')
add(io,'UADC','MCP3008 / DIP-16',[(i+1,'CH'+str(i),['ADC_BAT','CUR0','CUR1','CUR2','CUR3','RANGE_OUT','GND','GND'][i])for i in range(8)]+[(9,'DGND','GND'),(10,'nCS','SPI0_CS0'),(11,'DIN','SPI0_MOSI'),(12,'DOUT','SPI0_MISO'),(13,'CLK','SPI0_SCLK'),(14,'AGND','GND'),(15,'VREF','V3V3'),(16,'VDD','V3V3')])
add(io,'UDIR','MCP23S17 / DIP-28',[(9,'VDD','V3V3'),(10,'VSS','GND'),(11,'nCS','SPI0_CS1'),(12,'SCK','SPI0_SCLK'),(13,'SI','SPI0_MOSI'),(14,'SO','SPI0_MISO'),(15,'A0','GND'),(16,'A1','GND'),(17,'A2','GND'),(18,'nRESET','DIR_RST')]+[(21+i,'GPA'+str(i),'DIR'+str(i))for i in range(4)],'Remaining GPIO are unused inputs; enable pull-ups in software. INT pins unused.')
two(io,'RDIR','10k','V3V3','DIR_RST')
add(io,'UPWM','PCA9685 / TSSOP28',[(i+1,'A'+str(i),'GND')for i in range(5)]+[(6+i,'LED'+str(i),'PWM'+str(i))for i in range(4)]+[(14,'VSS','GND'),(23,'nOE','PWM_OE'),(24,'A5','GND'),(25,'SCL','I2C_SCL'),(26,'SDA','I2C_SDA'),(27,'EXTCLK','GND'),(28,'VDD','V3V3')],'Internal oscillator; nominal1017Hz. LED4..15 not connected. No 20kHz claim.')
two(io,'ROE','10k','V3V3','PWM_OE')
for side in (0,1):
 add(io,'UENC'+str(side),'LS7366R / DIP14',[(1,'fCKO',None),(2,'fCKi','ENC_CLK'),(3,'VSS','GND'),(4,'nSS','SPI1_CS'+str(side)),(5,'SCK','SPI1_SCLK'),(6,'MISO','SPI1_MISO'),(7,'MOSI','SPI1_MOSI'),(8,'nLFLAG',None),(9,'nDFLAG',None),(10,'nINDEX','V3V3'),(11,'B','ENC'+str(side)+'B'),(12,'A','ENC'+str(side)+'A'),(13,'CNT_EN','V3V3'),(14,'VDD','V3V3')])
 add(io,'JENC'+str(side),'Encoder / 3.3V quadrature',[(1,'3V3','V3V3'),(2,'A','ENC'+str(side)+'A'),(3,'B','ENC'+str(side)+'B'),(4,'GND','GND')],'2048 effective counts/wheel revolution required by current Java constants; otherwise change and recalibrate.')
add(io,'X1','1MHz CMOS oscillator / 3.3V',[(1,'OE','V3V3'),(2,'GND','GND'),(3,'OUT','ENC_CLK'),(4,'VDD','V3V3')],'Oscillator footprint/package pinout to be selected; interface numbers only.')
for ref in ['CADC','CDIR','CPWM','CENC0','CENC1','CX']:two(io,ref,'100nF at IC','V3V3','GND')
# Pi has I2C pullups; no blindly stacked pullups. GPIO sensors require external 10k resistors.
sensor=sheet('04_sensors','Dry-contact safety sensing / beam inputs / range output')
for ref,net,note in [('SES','ESTOP_SENSE','NC healthy; dedicated isolated auxiliary pole'),('SBU','BUMPER_SENSE','NC healthy; separate contact from CLEAR chain'),('SCL','CLIFF_SENSE','NC healthy from validated cliff detector'),('SGU','GUARD_SENSE','NC healthy'),('SFU','FULL_SENSE','NC healthy; opens when hopper full')]:
 two(sensor,ref,note,net,'GND');two(sensor,'R'+ref,'10k external pullup','V3V3',net)
for num,net in [(0,'INTAKE_BEAM'),(1,'DROP_BEAM')]:
 add(sensor,'JB'+str(num),'Beam receiver / open collector',[(1,'V5','V5'),(2,'OC_3V3',net),(3,'GND','GND')],'LOW=beam broken; 3.3V-safe open collector, not a 24V push-pull output');two(sensor,'RB'+str(num),'10k','V3V3',net)
for ref,net in [('RF','DRV_FAULT_N'),('RK','K1_SENSE')]:two(sensor,ref,'10k external pullup','V3V3',net)
add(sensor,'JRANGE','Calibrated analog obstacle sensor',[(1,'V5','V5'),(2,'OUT_0_3V3','RANGE_OUT'),(3,'GND','GND')],'Output must remain0..3.3V with external conditioning. Exact sensor/range curve is a commissioning decision; config rejects missing table.')
for i,name in enumerate(['left','right','brush','belt']):
 s=sheet(f'{5+i:02d}_motor_{name}',f'DRV8874 channel {i}: {name}')
 pref=f'M{i}_';vm=pref+'VM';ip=pref+'IP';vref=pref+'VREF'
 two(s,f'FM{i}', '4A DC'if i<2 else '3A DC','VM_BUS',vm)
 pins=[(1,'EN','PWM'+str(i)),(2,'PH','DIR'+str(i)),(3,'nSLEEP','RUN_EN'),(4,'nFAULT','DRV_FAULT_N'),(5,'VREF',vref),(6,'IPROPI',ip),(7,'IMODE','GND'),(8,'OUT1',pref+'OUT1'),(9,'PGND','GND'),(10,'OUT2',pref+'OUT2'),(11,'VM',vm),(12,'VCP',pref+'VCP'),(13,'CPH',pref+'CPH'),(14,'CPL',pref+'CPL'),(15,'GND','GND'),(16,'PMODE','GND'),(17,'PAD','GND')]
 add(s,f'UM{i}','DRV8874 HTSSOP16-PAD',pins,'PMODE=0 PH/EN. IMODE=0 current regulation; verify layout/thermal ratings, 6A peak is NOT continuous.')
 add(s,f'M{i}','24V brushed gearmotor',[(1,'+ / forward',pref+'OUT1'),(2,'- / reverse',pref+'OUT2')], '30rpm >=4Nm loaded'if i<2 else '120rpm brush'if i==2 else '45..60rpm conveyor')
 two(s,f'CCP{i}','22nF / 50V',pref+'CPH',pref+'CPL');two(s,f'CVCP{i}','100nF /16V',pref+'VCP',vm,'VCP capacitor is to VM, NOT ground')
 two(s,f'CVM{i}','100nF /50V',vm,'GND');two(s,f'CB{i}','470uF /50V',vm,'GND')
 two(s,f'RIP{i}','1.5k /1%',ip,'GND');two(s,f'RFLT{i}','10k',ip,'CUR'+str(i));two(s,f'CFLT{i}','100nF','CUR'+str(i),'GND','Filtered current reflects duty/recirculation; calibrate software thresholds')
 two(s,f'RVT{i}','6.19k /1%'if i<2 else '14.3k /1%','V3V3',vref);two(s,f'RVB{i}','10k /1%',vref,'GND')
 two(s,f'REN{i}','47k','PWM'+str(i),'GND');two(s,f'RPH{i}','47k','DIR'+str(i),'GND')
 two(s,f'CM{i}','10nF /50V motor suppression',pref+'OUT1',pref+'OUT2','Validate suppression with driver current/EMI; twist motor pair')
# Native KiCad schematic exporter. Functional module outlines are deliberate; no external symbol libraries required.
def q(s):return '"'+str(s).replace('\\','\\\\').replace('"','\\"')+'"'
def effect(size=1.0,extra=''):return f'(effects (font (size {size} {size})) {extra})'
def symbol_def(c):
 n=len(c['pins']);half=math.ceil(n/2);h=max(7,(half-1)*3.81/2+4);sid='UBOR:'+c['ref']
 out=f'(symbol {q(sid)} (pin_names (offset 0.8)) (in_bom yes) (on_board yes)\n'
 out+=f'(property "Reference" {q(c["ref"])} (at 0 {h+3} 0) {effect()}) (property "Value" {q(c["value"])} (at 0 {-h-3} 0) {effect()})\n'
 out+=f'(symbol {q(c["ref"]+"_0_1")} (rectangle (start -17 {h}) (end 17 {-h}) (stroke (width 0.254) (type default)) (fill (type background))))\n'
 out+=f'(symbol {q(c["ref"]+"_1_1")} '
 for idx,p in enumerate(c['pins']):
  left=idx<half;k=idx if left else idx-half;y=(half-1)*3.81/2-k*3.81;x=-22.08 if left else 22.08;ang=0 if left else 180
  p.update(localx=x,localy=y)
  out+=f'(pin passive line (at {x} {y} {ang}) (length 5.08) (name {q(p["name"])} {effect(.9)}) (number {q(p["number"])} {effect(.9)}))\n'
 return out+'))\n',h
svg_files=[]
for s in sheets:
 cs=s['components'];hlist=[];defs=[]
 for c in cs:
  df,h=symbol_def(c);defs.append(df);hlist.append(h)
 # Each schematic uses four columns, dynamically sized rows. Extend the sheet if required.
 positions=[];y=35
 for start in range(0,len(cs),4):
  rowh=max(hlist[start:start+4])*2+24
  for j,c in enumerate(cs[start:start+4]):positions.append((55+j*100,y+rowh/2))
  y+=rowh
 height=max(297,y+20);width=420
 text=f'(kicad_sch (version 20231120) (generator "eeschema") (uuid {s["id"]}) (paper "User" {width} {height})\n(title_block (title {q(s["title"])}) (rev "R01 CONCEPT") (comment 1 "UNREVIEWED ERC / NO PCB / NO FABRICATION RELEASE"))\n(lib_symbols '+''.join(defs)+')\n'
 sv=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}mm" height="{height}mm" viewBox="0 0 {width} {height}"><rect width="100%" height="100%" fill="white"/><g font-family="DejaVu Sans" fill="#182f39">',f'<text x="10" y="12" font-size="4.2">{html.escape(s["title"])}</text><text x="10" y="19" font-size="2.2">R01 / canonical connection graph. KiCad ERC and PCB routing NOT performed.</text>']
 for c,(x,cy),h in zip(cs,positions,hlist):
  text+=f'(symbol (lib_id {q("UBOR:"+c["ref"])}) (at {x} {cy} 0) (unit 1) (in_bom yes) (on_board yes) (dnp no) (uuid {c["uuid"]})\n'
  text+=f'(property "Reference" {q(c["ref"])} (at {x} {cy-h-4} 0) {effect(1.1)}) (property "Value" {q(c["value"])} (at {x} {cy+h+4} 0) {effect(1.0)})\n'
  for p in c['pins']:text+=f'(pin {q(p["number"])} (uuid {uid()}))\n'
  text+=f'(instances (project {q(PROJECT)} (path {q("/"+ROOTID+"/"+s["uuid"])} (reference {q(c["ref"])}) (unit 1)))))\n'
  sv.append(f'<rect x="{x-17}" y="{cy-h}" width="34" height="{2*h}" fill="#eef4f4" stroke="#223d47" stroke-width=".25"/><text x="{x}" y="{cy-h-3.5}" text-anchor="middle" font-size="2.4">{c["ref"]}</text>')
  # Value outside the electrical pin field, split into short visual lines.
  import textwrap
  for j,line in enumerate(textwrap.wrap(c['value'],42)):sv.append(f'<text x="{x}" y="{cy+h+4+j*2.4}" text-anchor="middle" font-size="2">{html.escape(line)}</text>')
  for p in c['pins']:
   px=x+p['localx'];py=cy-p['localy'];left=p['localx']<0;end=px+(-5.08 if left else 5.08)
   sv.append(f'<path d="M {x+(-17 if left else 17)} {py} L {end} {py}" stroke="#325859" stroke-width=".2"/><text x="{x+(-16 if left else 16)}" y="{py-.7}" text-anchor="{"start"if left else"end"}" font-size="1.75">{html.escape(p["number"]+":"+p["name"])}</text>')
   if p['net'] is None:text+=f'(no_connect (at {px} {py}) (uuid {uid()}))\n';sv.append(f'<text x="{end}" y="{py}" font-size="2">X</text>');continue
   text+=f'(wire (pts (xy {px} {py}) (xy {end} {py})) (stroke (width 0) (type default)) (uuid {uid()}))\n'
   text+=f'(global_label {q(p["net"])} (shape input) (at {end} {py} {0 if left else 180}) {effect(.9)} (uuid {uid()}))\n'
   sv.append(f'<text x="{end+(-1 if left else 1)}" y="{py-.6}" text-anchor="{"end"if left else"start"}" font-size="1.8">{html.escape(p["net"])}</text>')
 text+=')\n';(DEST/(s['name']+'.kicad_sch')).write_text(text)
 sv.append('</g></svg>');svg=ROOT/'electronics/drawings'/(s['name']+'.svg');svg.write_text('\n'.join(sv));svg_files.append(str(svg))
# Root hierarchical sheet instances; shared global labels connect through this root project.
r=f'(kicad_sch (version 20231120) (generator "eeschema") (uuid {ROOTID}) (paper "A3") (lib_symbols)\n'
for i,s in enumerate(sheets):
 x=25+(i%3)*125;y=40+(i//3)*70
 r+=f'(sheet (at {x} {y}) (size 100 45) (fields_autoplaced) (stroke (width 0) (type default)) (fill (color 0 0 0 0)) (uuid {s["uuid"]}) (property "Sheetname" {q(s["title"])} (at {x} {y-2} 0) {effect(1.1)}) (property "Sheetfile" {q(s["name"]+".kicad_sch")} (at {x} {y+47} 0) {effect(1.1)}) (instances (project {q(PROJECT)} (path {q("/"+ROOTID)} (page {q(i+2)})))))\n'
r+='(sheet_instances (path "/" (page "1"))))\n';(DEST/(PROJECT+'.kicad_sch')).write_text(r)
(DEST/(PROJECT+'.kicad_pro')).write_text(json.dumps({'meta':{'filename':PROJECT+'.kicad_pro','version':1},'schematic':{'legacy_lib_dir':'','legacy_lib_list':[]}},indent=2))
# Canonical unambiguous wiring data, independent from drawing layout.
nets={}
for c in components:
 for p in c['pins']:
  if p['net'] is not None:nets.setdefault(p['net'],[]).append({'ref':c['ref'],'pin':p['number'],'pin_name':p['name'],'sheet':c['sheet']})
(ROOT/'electronics/connection_graph.json').write_text(json.dumps({'revision':'R01','components':components,'nets':nets},indent=2))
with (ROOT/'electronics/wiring.csv').open('w',encoding='utf-8-sig',newline='')as f:
 w=csv.writer(f);w.writerow(['net','ref','pin','pin_name','sheet'])
 for net,ends in sorted(nets.items()):
  for e in ends:w.writerow([net,e['ref'],e['pin'],e['pin_name'],e['sheet']])
with (ROOT/'electronics/bom.csv').open('w',encoding='utf-8-sig',newline='')as f:
 w=csv.writer(f);w.writerow(['reference','value_or_procurement_requirement','quantity','sheet','notes'])
 for c in components:w.writerow([c['ref'],c['value'],1,c['sheet'],c['note']])
export=ET.Element('export',version='D');ce=ET.SubElement(export,'components');ne=ET.SubElement(export,'nets')
for c in components:
 elem=ET.SubElement(ce,'comp',ref=c['ref']);ET.SubElement(elem,'value').text=c['value']
for i,(name,ends)in enumerate(sorted(nets.items()),1):
 el=ET.SubElement(ne,'net',code=str(i),name=name)
 for e in ends:ET.SubElement(el,'node',ref=e['ref'],pin=e['pin'])
ET.ElementTree(export).write(ROOT/'electronics/netlist.xml',encoding='utf-8',xml_declaration=True)
# Structural checks only. They are NOT an ERC, transient/SPICE or design certification.
assert len({c['ref']for c in components})==len(components)
for c in components:assert len(set(p['number']for p in c['pins']))==len(c['pins'])
for p in next(c for c in components if c['ref']=='JPI')['pins']:assert p['net'] not in ['BAT_RAW','BAT_SW','VM_BUS','V5']
for i in range(4):
 c=next(c for c in components if c['ref']==f'UM{i}');pin={p['number']:p['net']for p in c['pins']}
 assert pin['16']=='GND'and pin['3']=='RUN_EN'and pin['1']==f'PWM{i}'and pin['2']==f'DIR{i}'
 assert any(c['ref']==f'CVCP{i}'and{p['net']for p in c['pins']}=={f'M{i}_VCP',f'M{i}_VM'}for c in components)
# Balanced-expression validation, ignoring quoted strings.
for file in DEST.glob('*.kicad_sch'):
 depth=0;quoted=False;escape=False
 for ch in file.read_text():
  if escape:escape=False;continue
  if ch=='\\'and quoted:escape=True;continue
  if ch=='"':quoted=not quoted
  if not quoted:
   depth+=(ch=='(')-(ch==')');assert depth>=0,file
 assert depth==0 and not quoted,file
report={'components':len(components),'named_nets':len(nets),'child_schematics':len(sheets),'unique_references':True,'pin_numbers_unique':True,'pi_header_no_power_bus_short':True,'four_motor_control_pin_mappings':True,'charge_pump_caps_to_VM':True,'sexpressions_balanced':True,'kicad_erc_run':False,'kicad_gui_opened':False,'pcb_routed':False,'spice_simulated':False,'single_ended_nets':[n for n,e in nets.items()if len(e)<2]}
(ROOT/'verification/electronics_checks.json').write_text(json.dumps(report,indent=2));print(report)
