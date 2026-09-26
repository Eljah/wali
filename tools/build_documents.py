#!/usr/bin/env python3
"""Documentation build; all engineering visuals come from project CAD/connection data."""
from pathlib import Path
import re, html, json, io, textwrap, csv, tempfile, shutil
import cairosvg, fitz
from PIL import Image as PILImage, ImageFont
from reportlab.pdfgen import canvas
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, A3, landscape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import (BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, PageBreak, Image, Table, TableStyle, KeepTogether, XPreformatted, CondPageBreak)
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.lib.enums import TA_LEFT
ROOT=Path(__file__).resolve().parents[1]; DOC=ROOT/'docs'; TMP=Path(tempfile.mkdtemp(prefix='ubor_docs_'))
FONTS=Path('/usr/share/fonts/truetype/dejavu')
for name,file in [('D','DejaVuSans.ttf'),('DB','DejaVuSans-Bold.ttf'),('DI','DejaVuSans-Oblique.ttf'),('DM','DejaVuSansMono.ttf')]:
 pdfmetrics.registerFont(TTFont(name,str(FONTS/file)))
pdfmetrics.registerFontFamily('D',normal='D',bold='DB',italic='DI',boldItalic='DB')
NAVY=colors.HexColor('#163A45'); TEAL=colors.HexColor('#247477'); LIGHT=colors.HexColor('#EEF4F4'); GRAY=colors.HexColor('#54646B')

def svg_start(w,h,title):
 return [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><defs><marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#247477"/></marker></defs><rect width="100%" height="100%" fill="white"/><g font-family="DejaVu Sans" fill="#163A45">',f'<text x="30" y="40" font-size="27" font-weight="bold">{html.escape(title)}</text>']
def box(s,x,y,w,h,lines,fill='#EEF4F4'):
 s.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="{fill}" stroke="#247477" stroke-width="2"/>')
 start=y+(h-(len(lines)-1)*32)/2+8
 for i,l in enumerate(lines):
  ff=ImageFont.truetype(str(FONTS/('DejaVuSans-Bold.ttf' if i==0 else 'DejaVuSans.ttf')),25); fs=min(25,25*(w-28)/max(1,ff.getlength(l)))
  s.append(f'<text x="{x+w/2}" y="{start+i*32}" text-anchor="middle" font-size="{fs:.1f}" font-weight="{"bold" if i==0 else "normal"}">{html.escape(l)}</text>')
def arrow(s,x1,y1,x2,y2,label=None):
 s.append(f'<path d="M{x1} {y1} L{x2} {y2}" stroke="#247477" stroke-width="3" fill="none" marker-end="url(#a)"/>')
 if label:
  tw=ImageFont.truetype(str(FONTS/'DejaVuSans.ttf'),21).getlength(label)+12
  s.append(f'<rect x="{(x1+x2)/2-tw/2}" y="{(y1+y2)/2-31}" width="{tw}" height="27" fill="white"/>')
  s.append(f'<text x="{(x1+x2)/2}" y="{(y1+y2)/2-10}" text-anchor="middle" font-size="21">{html.escape(label)}</text>')
def finish(s,name):
 s.append('</g></svg>');text='\n'.join(s);(DOC/f'{name}.svg').write_text(text)
 cairosvg.svg2png(bytestring=text.encode(),write_to=str(DOC/f'{name}.png'),output_width=2100)

s=svg_start(1400,760,'UBOR-JAVA R01 / потоки управления')
box(s,30,90,300,120,['UVC-камера','640 × 360','JavaCV / OpenCV'])
box(s,400,90,520,120,['DL4J: две CNN','ROI → класс; полный кадр → v, ω'])
box(s,990,90,380,120,['Пульт Java','Swing / USB-геймпад','TCP + HMAC'])
box(s,30,330,300,140,['Датчики','энкодеры / токи','контакты / дальномер'])
box(s,400,305,520,190,['SafetyController / 50 Гц','lease + блокировки + состояния','DriveControl: кинематика + PI'])
box(s,990,330,380,140,['PiHardware / Pi4J','GPIO / SPI / I²C','MCP3008, LS7366R'])
box(s,30,605,580,110,['Независимое разрешение','watchdog + NC-контакты → ручной ARM'])
box(s,710,585,660,150,['Силовая часть и механика','K1 + 4 × DRV8874; PWM + PH','колёса / щётка / транспортёр'])
arrow(s,330,150,400,150);arrow(s,660,210,660,305,'предложения');arrow(s,1180,210,905,305,'команды')
arrow(s,330,400,400,400);arrow(s,920,400,990,400);arrow(s,1180,470,1180,585)
arrow(s,610,660,710,660);arrow(s,150,470,150,605)
s.append('<text x="830" y="550" text-anchor="middle" font-size="21">GPIO22: heartbeat только после успешного I/O</text>')
finish(s,'architecture')
s=svg_start(1400,780,'UBOR-JAVA R01 / аппаратное разрешение и повторный пуск')
box(s,30,100,370,110,['TPS3431','WDI от завершённого цикла','WD_OK, ~200 мс'])
box(s,495,100,420,110,['NC-цепь','E-stop / кожух / бампер / край'])
box(s,1010,90,360,140,['SN74LVC1G74','nCLR ← CLEAR_N','D = 1, nPRE = 1'])
box(s,30,345,370,105,['Кнопка ARM','новое ручное нажатие'])
box(s,495,345,420,105,['RC + Schmitt','CLK: положительный фронт'])
box(s,1010,345,360,105,['Q = HW_ENABLE','самопуска после сброса нет'])
box(s,30,590,530,120,['HW_ENABLE AND SOFT_ARM','RUN_EN → nSLEEP','NOT RUN_EN → PWM OE'])
box(s,700,590,670,120,['HW_ENABLE → буфер 5 В → MOSFET','Катушка K1 + второй NC полюс E-stop','Разрыв питания моторной шины'])
arrow(s,400,155,495,155);arrow(s,915,155,1010,155)
arrow(s,400,397,495,397);arrow(s,915,397,975,397)
s.append('<path d="M975 397 L975 260 L1100 260 L1100 230" stroke="#247477" stroke-width="3" fill="none" marker-end="url(#a)"/>')
arrow(s,1285,230,1285,345);arrow(s,1190,450,1190,590)
s.append('<path d="M1050 450 L1050 510 L295 510 L295 590" stroke="#247477" stroke-width="3" fill="none" marker-end="url(#a)"/>')
s.append('<text x="30" y="755" font-size="22">Проектный контур. Не заявлены PL/SIL, измеренная задержка K1 или выполненное SPICE-испытание.</text>')
finish(s,'safety_chain')

styles={
 'body':ParagraphStyle('body',fontName='D',fontSize=10.15,leading=14.5,spaceAfter=8,textColor=colors.HexColor('#243B42'),allowWidows=0,allowOrphans=0),
 'h1':ParagraphStyle('h1',fontName='DB',fontSize=20,leading=25,spaceAfter=20,textColor=NAVY,keepWithNext=True),
 'h2':ParagraphStyle('h2',fontName='DB',fontSize=13.2,leading=18,spaceBefore=12,spaceAfter=8,textColor=NAVY,keepWithNext=True),
 'caption':ParagraphStyle('caption',fontName='DI',fontSize=8.8,leading=12,spaceBefore=7,spaceAfter=14,textColor=GRAY),
 'code':ParagraphStyle('code',fontName='DM',fontSize=8.15,leading=11.5,backColor=LIGHT,borderPadding=9,spaceBefore=6,spaceAfter=14),
 'cell':ParagraphStyle('cell',fontName='D',fontSize=8.5,leading=11.7,textColor=NAVY),
 'cellhead':ParagraphStyle('cellhead',fontName='DB',fontSize=8.5,leading=11.7,textColor=colors.white),
 'reference':ParagraphStyle('reference',fontName='D',fontSize=9.25,leading=12.5,spaceAfter=6,textColor=GRAY),
 'quote':ParagraphStyle('quote',fontName='D',fontSize=10,leading=14.4,textColor=NAVY),
}
W,H=A4;M=52;CW=W-2*M

def inline(s):
 s=html.escape(s)
 s=re.sub(r'`([^`]+)`',r'<font name="DM">\1</font>',s)
 s=re.sub(r'\*\*([^*]+)\*\*',r'<b>\1</b>',s)
 return s

def parse_md(path):
 lines=path.read_text().splitlines();out=[];i=0;first=True;heading=0
 while i<len(lines):
  ln=lines[i].strip()
  if not ln:i+=1;continue
  if ln.startswith('# '):
   if not first:out.extend([CondPageBreak(280),Spacer(1,16)])
   first=False;heading+=1;p=Paragraph(inline(ln[2:]),styles['h1']);p._ubor_key='chapter'+str(heading);out.append(p);i+=1;continue
  if ln.startswith('## '):out.append(Paragraph(inline(ln[3:]),styles['h2']));i+=1;continue
  if ln.startswith('```'):
   code=[];i+=1
   while i<len(lines)and not lines[i].startswith('```'):code.append(lines[i]);i+=1
   text='\n'.join('\n'.join(textwrap.wrap(line,95,replace_whitespace=False,drop_whitespace=False)) if len(line)>95 else line for line in code);out.append(XPreformatted(html.escape(text),styles['code']));i+=1;continue
  img=re.match(r'!\[(.*?)\]\((.*?)\)',ln)
  if img:
   file=ROOT/img.group(2);im=PILImage.open(file);iw,ih=im.size;scale=min(CW/iw,310/ih)
   out.append(KeepTogether([Image(str(file),width=iw*scale,height=ih*scale),Paragraph(inline(img.group(1)),styles['caption'])]));i+=1;continue
  if ln.startswith('|'):
   rows=[]
   while i<len(lines) and lines[i].strip().startswith('|'):
    cells=[c.strip()for c in lines[i].strip().strip('|').split('|')]
    if not all(re.fullmatch(r'[-: ]+',c)for c in cells):rows.append(cells)
    i+=1
   n=len(rows[0]);fractions={2:[.38,.62],3:[.27,.33,.40],4:[.31,.12,.18,.39]}.get(n,[1/n]*n)
   data=[[Paragraph(inline(c),styles['cellhead'] if j==0 else styles['cell'])for c in row]for j,row in enumerate(rows)]
   table=Table(data,colWidths=[CW*v for v in fractions],repeatRows=1,hAlign='LEFT')
   table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),NAVY),('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white,LIGHT]),('LINEBELOW',(0,0),(-1,0),.8,TEAL),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7),('TOPPADDING',(0,0),(-1,-1),7),('BOTTOMPADDING',(0,0),(-1,-1),7)]))
   out.extend([table,Spacer(1,13)]);continue
  if ln.startswith('> '):
   t=Table([[Paragraph(inline(ln[2:]),styles['quote'])]],colWidths=[CW]);t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,-1),LIGHT),('BOX',(0,0),(-1,-1),.6,TEAL),('LEFTPADDING',(0,0),(-1,-1),12),('RIGHTPADDING',(0,0),(-1,-1),12),('TOPPADDING',(0,0),(-1,-1),11),('BOTTOMPADDING',(0,0),(-1,-1),11)]));out.extend([t,Spacer(1,12)]);i+=1;continue
  paras=[ln];i+=1
  while i<len(lines)and lines[i].strip()and not lines[i].startswith(('#','|','```','![')):
   paras.append(lines[i].strip());i+=1
  out.append(Paragraph(inline(' '.join(paras)),styles['reference'] if re.match(r'^\[\d+\]',paras[0]) else styles['body']))
 return out

class BookDoc(BaseDocTemplate):
 def __init__(self,file):
  super().__init__(str(file),pagesize=A4,leftMargin=M,rightMargin=M,topMargin=57,bottomMargin=54,title='UBOR-JAVA R01 — робот-сборщик мусора',author='Инженерный проект UBOR-JAVA',pageCompression=1)
  self.addPageTemplates(PageTemplate(id='body',frames=[Frame(M,54,CW,H-111,id='content',leftPadding=0,rightPadding=0,topPadding=0,bottomPadding=0)],onPage=self.decor))
 def decor(self,c,doc):
  c.setStrokeColor(TEAL);c.setLineWidth(.6);c.line(M,H-34,W-M,H-34)
  c.setFont('D',8);c.setFillColor(GRAY);c.drawString(M,H-25,'UBOR-JAVA R01  /  ПРАКТИЧЕСКАЯ КНИГА ПРОЕКТА')
  c.drawString(M,30,'Инженерная проверка концепции · 23.09.2026');c.drawRightString(W-M,30,str(doc.page+1))
 def afterFlowable(self,f):
  if hasattr(f,'_ubor_key'):
   text=f.getPlainText();self.canv.bookmarkPage(f._ubor_key);self.canv.addOutlineEntry(text,f._ubor_key,level=0,closed=False)
   self.notify('TOCEntry',(0,text,self.page+1,f._ubor_key))

cover=TMP/'cover.pdf';c=canvas.Canvas(str(cover),pagesize=A4)
c.setFillColor(colors.HexColor('#F4F6F8'));c.rect(0,0,W,H,fill=1,stroke=0)
c.setFillColor(TEAL);c.rect(0,H-15,W,15,fill=1,stroke=0)
c.setFont('DB',12);c.setFillColor(TEAL);c.drawString(M,H-63,'МЕХАНИКА  /  ЭЛЕКТРОНИКА  /  JAVA  /  ML')
c.setFont('DB',37);c.setFillColor(NAVY);c.drawString(M,H-120,'UBOR-JAVA')
c.setFont('DB',24);c.drawString(M,H-159,'Робот-сборщик мусора')
c.setFont('D',13);c.setFillColor(GRAY);c.drawString(M,H-194,'Практическая книга инженерного проекта R01')
c.drawImage(str(ROOT/'cad/render/assembly.png'),30,190,width=W-60,height=390,preserveAspectRatio=True,anchor='c',mask='auto')
c.setFillColor(NAVY);c.roundRect(M,104,CW,72,6,fill=1,stroke=0)
p=Paragraph('От параметрической CAD-сборки до пульта и обучения CNN.<br/>Исходники, расчёты, протоколы и честные границы проверки.',ParagraphStyle('coverbox',fontName='D',fontSize=11,leading=16,textColor=colors.white));p.wrap(CW-26,65);p.drawOn(c,M+13,125)
c.setFont('DB',10);c.setFillColor(NAVY);c.drawString(M,75,'23 сентября 2026  ·  Инженерный proof of concept')
c.setFont('D',8.7);c.setFillColor(GRAY);c.drawString(M,54,'Не производственный выпуск. Аппаратные испытания и обучение ещё не выполнены.')
c.showPage();c.save()

toc=TableOfContents();toc.levelStyles=[ParagraphStyle('toc0',fontName='D',fontSize=9.3,leading=12.7,spaceBefore=3,leftIndent=0,firstLineIndent=0,textColor=NAVY)]
toc.tableStyle.add('TOPPADDING',(0,0),(-1,-1),0)
toc.tableStyle.add('BOTTOMPADDING',(0,0),(-1,-1),0)
story=[Paragraph('Содержание',styles['h1']),Paragraph('24 главы, предисловие и три приложения. Номера страниц и ссылки ведут к соответствующим разделам.',styles['body']),Spacer(1,10),toc,PageBreak()]
story+=parse_md(DOC/'BOOK_RU.md')
body=TMP/'book_body.pdf';BookDoc(body).multiBuild(story)
book=fitz.open();book.insert_pdf(fitz.open(cover));bdoc=fitz.open(body);book.insert_pdf(bdoc)
toclist=bdoc.get_toc();book.set_toc([[l,t,p+1]for l,t,p in toclist]);book.set_metadata({'title':'UBOR-JAVA R01 — робот-сборщик мусора','author':'UBOR-JAVA engineering project','subject':'CAD, электроника, Java, Pi4J, DL4J и проверка концепции'})
book.save(DOC/'UBOR_JAVA_BOOK_RU.pdf',garbage=4,deflate=True);print('BOOK pages',len(book))

# Engineering drawing portfolio. Projections are inserted as vector pages derived from actual CAD SVGs.
MW,MH=landscape(A3)
def draw_paragraph(c,text,x,y,w,size=11):
 p=Paragraph(inline(text),ParagraphStyle('pdraw',fontName='D',fontSize=size,leading=size*1.45,textColor=NAVY));_,hh=p.wrap(w,900);p.drawOn(c,x,y-hh);return hh

def drawing_frame(c,title,num):
 c.setStrokeColor(NAVY);c.setLineWidth(.7);c.rect(22,22,MW-44,MH-44)
 c.line(22,72,MW-22,72);c.setFont('DB',17);c.setFillColor(NAVY);c.drawString(38,MH-52,title)
 c.setFont('D',10);c.drawString(38,53,'UBOR-JAVA R01 · ЭСКИЗНЫЙ ПРОЕКТ · не для изготовления')
 c.drawString(38,36,'CAD: CadQuery / OpenCascade. Размеры в мм. Изображения не задают масштаб печати.')
 c.drawRightString(MW-38,50,f'Лист {num}/5   |   23.09.2026')

base=TMP/'mechanical_base.pdf';c=canvas.Canvas(str(base),pagesize=(MW,MH));placements=[]
drawing_frame(c,'М01 / Общая компоновка и единый CAD-источник',1)
c.drawImage(str(ROOT/'cad/render/assembly.png'),35,96,width=750,height=650,preserveAspectRatio=True,anchor='c')
y=MH-115
for line in ['121 именованное твердотельное тело.','Габарит STEP: 809,5 × 545,0 × 665,0.','Рама: 660 × 430; профиль 20 × 20 × 2.','Колёса Ø200; колея 500.','Рабочая ширина: 320.','Бункер: 24,12 л; полезная нагрузка до 4 кг.','Расчётная полная масса: 30 кг.','Оценка массы модели: 24,31 кг.','STEP + отдельные детали + параметрический исходник. Нативные SolidWorks-файлы не созданы.','Покупные интерфейсы, посадки, крепёж и натяжитель требуют детализации.']:
 y-=draw_paragraph(c,line,815,y,MW-855,11)+13
c.showPage()
drawing_frame(c,'М02 / Ортогональные виды из общей CAD-сборки',2)
# Source render canvases have whitespace; vector insertion uses cropped bounding boxes from raster-only extent inspection.
for name,title,rect in [('top','Вид сверху / X–Y',(40,105,530,400)),('side','Вид сбоку / X–Z',(600,100,1150,450)),('front','Вид спереди / Y–Z',(70,440,520,760))]:
 # rectangle passed in top-left PDF coordinates later
 c.setFont('DB',12);c.drawString(rect[0],MH-rect[1]+12,title);placements.append((1,name,rect))
draw_paragraph(c,'Габарит: 809,5 × 545,0 × 665,0.\nКолёса Ø200, колея 500. Ширина транспортёра 320.\nПроекции построены ядром CAD; посадочные и монтажные размеры по ним не восстанавливать.',625,250,485,12)
c.showPage()
drawing_frame(c,'М03 / Размерные связи транспортёра и бункера',3)
c.drawImage(str(ROOT/'cad/render/cutaway.png'),35,130,width=750,height=610,preserveAspectRatio=True,anchor='c')
y=MH-110
for line in ['Ось нижнего ролика: (300; 0; 85).','Ось верхнего ролика: (−30; 0; 445).','Межосевое расстояние: 488,36.','Угол транспортёра: 47,49°.','Ролики R25; расчётный радиус средней линии ленты 26,5.','Средняя линия ленты: около 1143,23. Не заказная длина без выбора типа полотна.','Щётка: ось (383; 0; 62), наружный R57, ширина 320.','Бункер снаружи 320 × 360 × 220, стенка/дно 3.','Натяжитель и все защитные ограждения должны быть детализированы до моторных испытаний.']:
 y-=draw_paragraph(c,line,815,y,MW-855,11)+13
c.showPage()
drawing_frame(c,'М04 / Обслуживание и порядок сборки',4)
c.drawImage(str(ROOT/'cad/render/rear.png'),35,155,width=720,height=575,preserveAspectRatio=True,anchor='c')
y=MH-110
for line in ['Бункер извлекается назад. Service STEP: смещение на −420 по X.','Отключить S0, отсоединить источник, дождаться неподвижности и измерить остаточное VM.','Рама → ведущие опоры → батарейная опора → транспортёр → бункер → электроника → проводка.','Проверить стопорение валов, крепление батареи, свободный ход ленты и сервисную зону.','Силуэт CAD не подтверждает отсутствие всех контактов и опасных точек.','Блокировка наличия бункера отдельно не реализована. Сервис только при отключённом питании.']:
 y-=draw_paragraph(c,line,790,y,MW-830,12)+17
c.showPage()
drawing_frame(c,'М05 / Предварительные расчёты и границы выпуска',5)
y=MH-105
rows=[('Тяга','m=30 кг; α=5°; c_r=0,03; a=0,25 м/с². F≈41,95 Н. Требование с запасом 1,5: 3,15 Н·м на колесо. Предварительно выбрать ≥4 Н·м длительно при ~30 об/мин под нагрузкой.'),('Масса','Оценка модели 24,31 кг; +4 кг груза оставляет 1,69 кг до 30 кг. Реальное взвешивание обязательно.'),('Балка','I=7872 мм⁴; простая схема даёт ~30,84 МПа и ~1,62 мм. Не FEA, не расчёт соединений и усталости.'),('Энергия','25,6 В × 10 А·ч = 256 Вт·ч. При условных 80 Вт и принятых коэффициентах 2,30 часа; это не измеренная автономность.'),('Остановка','При v=0,3 м/с, задержке 0,3 с, замедлении 0,6 м/с² и запасе 0,1 м: 0,265 м. Параметры не измерены; датчик и переднюю кромку необходимо согласовать.'),('Следующий выпуск','Выбрать реальные моторы/редукторы, подшипники, посадки, стопорение, ступицы, муфты и крепёж; детализировать натяжитель, защиты, кабели и удержание батареи.')]
for title,text in rows:
 c.setFillColor(LIGHT);c.rect(40,y-81,MW-80,80,fill=1,stroke=0);c.setFillColor(NAVY);c.setFont('DB',12);c.drawString(55,y-24,title);draw_paragraph(c,text,225,y-12,MW-290,11.5);y-=101
c.showPage();c.save()
mdoc=fitz.open(base)
for pageidx,name,rect in placements:
 sv=ROOT/'cad/drawings'/f'{name}.svg';data=cairosvg.svg2pdf(url=str(sv));src=fitz.open(stream=data,filetype='pdf');page=src[0]
 pix=page.get_pixmap(matrix=fitz.Matrix(1,1),alpha=False);im=PILImage.frombytes('RGB',[pix.width,pix.height],pix.samples)
 import numpy as np
 arr=np.asarray(im);ys,xs=np.where(np.min(arr,axis=2)<230);clip=fitz.Rect(max(0,xs.min()-4),max(0,ys.min()-4),min(page.rect.width,xs.max()+5),min(page.rect.height,ys.max()+5))
 mdoc[pageidx].show_pdf_page(fitz.Rect(*rect),src,0,clip=clip)
mdoc.set_metadata({'title':'UBOR-JAVA R01 / Эскизные механические виды','author':'UBOR-JAVA'})
mdoc.save(ROOT/'cad/drawings/UBOR_MECHANICS_R01.pdf',garbage=4,deflate=True)

# Electrical portfolio: functional architecture cover + 8 canonical per-pin schematic sheets.
ebase=TMP/'electrical_cover.pdf';c=canvas.Canvas(str(ebase),pagesize=landscape(A3))
c.setFont('DB',25);c.setFillColor(NAVY);c.drawString(35,MH-55,'UBOR-JAVA R01 / Электроника')
draw_paragraph(c,'141 компонент · 100 именованных цепей · 8 дочерних схем KiCad. Вид PDF построен из канонического графа соединений, не экспортирован приложением KiCad.',35,MH-78,MW-70,13)
c.drawImage(str(DOC/'architecture.png'),35,195,width=MW-70,height=505,preserveAspectRatio=True,anchor='c')
draw_paragraph(c,'СТАТУС: структурная проверка выполнена. KiCad не запускался; ERC, PCB, DRC и SPICE не выполнены. Условные модульные разъёмы не являются готовыми footprint. Силовой выпуск запрещён до проверки рекуперации, тепла, предзаряда и выбранных компонентов.',35,175,MW-70,13)
draw_paragraph(c,'Главный источник: electronics/connection_graph.json и wiring.csv. Корневой проект: electronics/kicad/UBOR_Electronics.kicad_pro. Отдельные NC-контакты состояния не соединять с цепями 24 В.',35,80,MW-70,10)
c.showPage();c.save()
e=fitz.open(ebase)
for sv in sorted((ROOT/'electronics/drawings').glob('0*.svg')):
 data=cairosvg.svg2pdf(url=str(sv));e.insert_pdf(fitz.open(stream=data,filetype='pdf'))
e.set_metadata({'title':'UBOR-JAVA R01 / Электрические схемы соединений','author':'UBOR-JAVA'})
e.save(ROOT/'electronics/drawings/UBOR_ELECTRONICS_R01.pdf',garbage=4,deflate=True)
summary={'book_pages':len(book),'book_chapters':24,'book_figures':7,'mechanical_pdf_pages':len(mdoc),'electrical_pdf_pages':len(e),'markdown_words':len((DOC/'BOOK_RU.md').read_text().split())}
(ROOT/'verification/document_build.json').write_text(json.dumps(summary,indent=2));print(summary)

shutil.rmtree(TMP)
