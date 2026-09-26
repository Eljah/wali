from pathlib import Path
import json,html
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, Preformatted, Image, KeepTogether
import fitz
P=Path(__file__).resolve().parent.parent
for name,file in [('Body','DejaVuSans.ttf'),('Bold','DejaVuSans-Bold.ttf'),('Mono','DejaVuSansMono.ttf')]:
 pdfmetrics.registerFont(TTFont(name,'/usr/share/fonts/truetype/dejavu/'+file))
navy=colors.HexColor('#15333F'); teal=colors.HexColor('#007C83'); gray=colors.HexColor('#56636C'); pale=colors.HexColor('#EDF5F5'); border=colors.HexColor('#CDD9DF')
styles={
 'p':ParagraphStyle('p',fontName='Body',fontSize=10.15,leading=15,spaceAfter=11,textColor=navy),
 'h':ParagraphStyle('h',fontName='Bold',fontSize=12,leading=16,spaceBefore=10,spaceAfter=8,textColor=teal),
 'title':ParagraphStyle('title',fontName='Bold',fontSize=21,leading=27,spaceAfter=20,textColor=navy),
 'cell':ParagraphStyle('cell',fontName='Body',fontSize=8.8,leading=12,textColor=navy),
 'th':ParagraphStyle('th',fontName='Bold',fontSize=8.8,leading=12,textColor=colors.white),
 'callout':ParagraphStyle('callout',fontName='Bold',fontSize=9.5,leading=14,textColor=navy),
 'mono':ParagraphStyle('mono',fontName='Mono',fontSize=8,leading=12,textColor=navy),
}
def par(s,style='p'):return Paragraph(html.escape(s),styles[style])
def footer(c,d):
 c.saveState();w,h=A4
 c.setStrokeColor(border);c.line(48,h-42,w-48,h-42)
 c.setFont('Bold',8);c.setFillColor(teal);c.drawString(48,h-31,'UBOR-JAVA  /  R02  /  WPILib')
 c.setFont('Body',7.5);c.setFillColor(gray);c.drawString(48,29,'Исходная интеграция • запуск WPILib не подтверждён')
 c.drawRightString(w-48,29,f'Приложение · {d.page}')
 c.restoreState()
sections=json.loads((P/'tools/wpilib_chapter_sections.json').read_text())
story=[];width=A4[0]-96
for idx,(title,items) in enumerate(sections):
 if idx:story.append(PageBreak())
 story.append(par(title,'title'))
 for kind,value in items:
  if kind in ('p','h'):story.append(par(value,kind))
  elif kind=='callout':
   t=Table([[par(value,'callout')]],colWidths=[width]);t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,-1),pale),('BOX',(0,0),(-1,-1),.5,border),('LEFTPADDING',(0,0),(-1,-1),12),('RIGHTPADDING',(0,0),(-1,-1),12),('TOPPADDING',(0,0),(-1,-1),10),('BOTTOMPADDING',(0,0),(-1,-1),10)]));story.extend([t,Spacer(1,13)])
  elif kind=='code':
   t=Table([[Preformatted(value,styles['mono'])]],colWidths=[width]);t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,-1),colors.HexColor('#F3F5F7')),('LEFTPADDING',(0,0),(-1,-1),10),('TOPPADDING',(0,0),(-1,-1),10),('BOTTOMPADDING',(0,0),(-1,-1),10)]));story.extend([t,Spacer(1,12)])
  elif kind=='table':
   rows=[[par(c,'th' if n==0 else 'cell') for c in row] for n,row in enumerate(value)]
   t=Table(rows,colWidths=[width*.39,width*.61],repeatRows=1,hAlign='LEFT');t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),navy),('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white,pale]),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),9),('RIGHTPADDING',(0,0),(-1,-1),9),('TOPPADDING',(0,0),(-1,-1),8),('BOTTOMPADDING',(0,0),(-1,-1),8),('LINEBELOW',(0,0),(-1,0),1,teal)]));story.extend([t,Spacer(1,13)])
  else:raise ValueError(kind)
pdf=P/'docs/25_WPILIB_R02.pdf'
SimpleDocTemplate(str(pdf),pagesize=A4,rightMargin=48,leftMargin=48,topMargin=64,bottomMargin=55,title='UBOR JAVA R02: интеграция WPILib',author='UBOR Project').build(story,onFirstPage=footer,onLaterPages=footer)
# A revision cover; original R01 pages are preserved, not relabelled as new validation.
cover=P/'tools/.generated-r02-title.pdf'
coverstyle=ParagraphStyle('cover',parent=styles['title'],fontSize=29,leading=36,spaceAfter=20)
coverbody=[Spacer(1,12),Paragraph('UBOR-JAVA',coverstyle),par('R02 · Симуляция на WPILib','title'),par('Книга проекта робота-сборщика мусора'),Spacer(1,12),Image(str(P/'cad/render/assembly.png'),width=width,height=width*0.38,kind='proportional'),Spacer(1,18),par('Что нового в этой редакции','h'),par('Параметрическая модель приводов, виртуальные датчики HAL, телеметрия Field2d и сменный физический backend. Общий Java-контроллер и пульт сохраняются.'),par('Статус выпуска','h'),par('Интеграция WPILib включена в исходники. Бинарные зависимости в среде подготовки недоступны; компиляция и исполнение WPILib не проверены. Выполненные проверки ядра и интерфейса не заменяют эту проверку.'),Spacer(1,10),par('Далее без изменений приведена книга R01 (37 страниц). Новая глава 25 находится после неё. Прежние результаты относятся к R01; актуальный статус R02 дан в приложении и verification/REPORT_R02_RU.md.'),Spacer(1,8),par('23 сентября 2026 · инженерный прототип, не документация для немедленного изготовления')]
SimpleDocTemplate(str(cover),pagesize=A4,rightMargin=48,leftMargin=48,topMargin=56,bottomMargin=52,title='UBOR R02',author='UBOR Project').build(coverbody,onFirstPage=footer,onLaterPages=footer)
old=fitz.open(P/'docs/UBOR_JAVA_BOOK_RU.pdf');new=fitz.open();c=fitz.open(cover);supp=fitz.open(pdf)
new.insert_pdf(c);new.insert_pdf(old);new.insert_pdf(supp)
new.set_toc([[1,'Редакция R02: статус',1],[1,'Книга R01 (без изменений)',len(c)+1],[1,'25. Интеграция WPILib',len(c)+len(old)+1]])
new.set_metadata({'title':'UBOR-JAVA R02 — книга с главой о WPILib','author':'UBOR Project','subject':'Исходная интеграция WPILib; runtime не проверен'})
new.save(P/'docs/UBOR_JAVA_BOOK_R02.pdf',garbage=4,deflate=True)
print('cover pages',len(c),'R01',len(old),'supplement',len(supp),'full',len(new))
qa=P/'verification/docs-r02-qa';qa.mkdir(exist_ok=True)
for i,page in enumerate(supp):page.get_pixmap(matrix=fitz.Matrix(1.3,1.3)).save(qa/f'chapter-{i+1}.png')
for i,page in enumerate(c):page.get_pixmap(matrix=fitz.Matrix(1.3,1.3)).save(qa/f'cover-{i+1}.png')
# Recheck original page text remained identical in merged output.
merged=fitz.open(P/'docs/UBOR_JAVA_BOOK_R02.pdf')
assert all(old[i].get_text()==merged[len(c)+i].get_text() for i in range(len(old)))
assert len(c)==1, 'Cover overflowed'
# layout bounds and long code checks, excluding running heads/feet
for path in (pdf,cover):
 doc=fitz.open(path)
 for i,pg in enumerate(doc):
  for b in pg.get_text('dict')['blocks']:
   if 'lines' not in b:continue
   for ln in b['lines']:
    for s in ln['spans']:
     x0,y0,x1,y1=s['bbox']
     assert x0>=35 and x1<=A4[0]-30,(path,i,s['text'],s['bbox'])
print('Preserved R01 text and horizontal layout bounds: PASS')

cover.unlink(missing_ok=True)
