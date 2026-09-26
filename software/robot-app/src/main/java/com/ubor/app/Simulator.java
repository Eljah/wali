package com.ubor.app;

import com.ubor.core.*;
import static com.ubor.core.Types.*;
import java.awt.*;
import com.ubor.core.Types.Label;
import com.ubor.core.Types.Frame;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Low-order 2D plant, not a rigid-body contact / FEM or semiconductor simulator. */
public final class Simulator implements Hardware,Perception {
    public static final class Litter {
        public final double x,y;public final Label label;
        public int stage=0; // 0 floor, 1 on conveyor, 2 deposited
        double progress;
        public Litter(double x,double y,Label label) { this.x=x;this.y=y;this.label=label; }
    }
    private final List<Litter> litter=new ArrayList<>();
    private final SimDynamics dynamics;
    private boolean closed;
    private double brushOmega,beltSpeed,leftCurrent,rightCurrent,brushCurrent,beltCurrent;
    public String dynamicsName() { return dynamics==null?"LEGACY_FIRST_ORDER":dynamics.name(); }
    public synchronized SimDynamics.State physicsState() { return dynamics==null?null:dynamics.state(); }
    /** Explicit simulated physical ARM, independent of the software ARM bit. */
    public synchronized boolean physicalArm() {
        if (emergency||bumper||cliff||guardOpen) return false;
        permit=true; return true;
    }
    public double x=0,y=0,heading=0,leftSpeed,rightSpeed;
    public boolean emergency,bumper,cliff,guardOpen,full,permit=true,jam;
    private double leftDistance,rightDistance,battery=25.6;
    private long lastTime=Long.MIN_VALUE,lastApply=Long.MIN_VALUE,dropUntil;
    private Actuation output=Actuation.off();
    private boolean intake;
    private int deposited;
    public Simulator() { this(null); }
    public Simulator(SimDynamics dynamics) {
        this.dynamics=dynamics;
        litter.add(new Litter(1.5,.05,Label.PAPER));
        litter.add(new Litter(2.7,-.08,Label.METAL_CAN));
        litter.add(new Litter(4,.12,Label.PLASTIC_BOTTLE));
        litter.add(new Litter(6,-1.1,Label.HAZARD));
    }
    public synchronized void advance(long now) {
        if(closed) throw new IllegalStateException("Simulator is closed");
        if(lastTime==Long.MIN_VALUE) { lastTime=now;return; }
        if(now<=lastTime)return;
        long from=lastTime;double dt=(now-lastTime)*1e-9;lastTime=now;
        if(dt<=0) return;
        // Model an independent watchdog. A new process cannot re-close its physical relay.
        // Long pauses are explicitly rejected instead of silently losing simulated time.
        if(dt>5) { output=Actuation.off();permit=false;throw new IllegalStateException("Simulation time gap exceeds 5 seconds"); }
        int steps=Math.max(1,(int)Math.ceil(dt/(dynamics==null?.01:.005)));double ds=dt/steps;
        intake=false;
        for(int step=0;step<steps;step++) {
            long stepTime=from+Math.round((step+1)*ds*1e9);
            if(lastApply!=Long.MIN_VALUE && stepTime-lastApply>200_000_000L) { output=Actuation.off();permit=false; }
            boolean powered=output.enabled()&&permit&&!emergency&&!bumper&&!cliff&&!guardOpen;
            if (emergency||bumper||cliff||guardOpen) permit=false;
            powered &= permit;
            if (dynamics!=null) {
                SimDynamics.State m=dynamics.step(ds,output,powered,jam);
                x=m.x();y=m.y();heading=m.heading();leftSpeed=m.leftVelocity();rightSpeed=m.rightVelocity();
                leftDistance=m.leftPosition();rightDistance=m.rightPosition();battery=m.batteryV();
                brushOmega=m.brushOmega();beltSpeed=m.beltSpeed();
                leftCurrent=m.leftCurrent();rightCurrent=m.rightCurrent();brushCurrent=m.brushCurrent();beltCurrent=m.beltCurrent();
            } else {
                double ls=powered?output.left()*.40:0,rs=powered?output.right()*.40:0;
                if(powered) { leftSpeed+=(ls-leftSpeed)*Math.min(1,ds/.18);rightSpeed+=(rs-rightSpeed)*Math.min(1,ds/.18); }
                else { leftSpeed=approach(leftSpeed,0,.6*ds);rightSpeed=approach(rightSpeed,0,.6*ds); }
                double v=(leftSpeed+rightSpeed)/2,w=(rightSpeed-leftSpeed)/.5;
                heading+=w*ds;x+=v*Math.cos(heading)*ds;y+=v*Math.sin(heading)*ds;
                leftDistance+=leftSpeed*ds;rightDistance+=rightSpeed*ds;
                brushOmega=powered&&!jam?output.brush()*18.85:0;
                beltSpeed=powered&&!jam?output.belt()*.27:0;
            }
            if(!jam) for(Litter p:litter) {
                double[] rel=relative(p.x,p.y);
                if(p.stage==0&&Math.abs(rel[0]-.63)<.12&&Math.abs(rel[1])<.13
                        &&brushOmega>3.77&&beltSpeed>.054) { p.stage=1;intake=true; }
                if(p.stage==1) {
                    intake |= p.progress<.025;
                    p.progress+=Math.max(0,beltSpeed)*ds;
                    if(p.progress>=.4884) { p.stage=2;deposited++;dropUntil=now+200_000_000L; }
                }
            }
        }
        if(Math.abs(y)>2.15||x>6.65||x<-.8) bumper=true;
        if(dynamics==null) battery-=dt*(Math.abs(output.left())+Math.abs(output.right())+output.brush()+output.belt())*.00012;
    }
    private static double approach(double a,double b,double delta) { return a+clamp(b-a,-delta,delta); }
    public synchronized double[] relative(double wx,double wy) {
        double dx=wx-x,dy=wy-y;return new double[]{dx*Math.cos(heading)+dy*Math.sin(heading),-dx*Math.sin(heading)+dy*Math.cos(heading)};
    }
    public synchronized double frontRange() {
        double range=4;
        for(double[] obstacle:new double[][]{{4.7,1.25,.40}}) {
            double[] r=relative(obstacle[0],obstacle[1]);
            if(r[0]>.0 && Math.abs(r[1])<obstacle[2]+.28) range=Math.min(range,Math.max(0,r[0]-obstacle[2]-.63));
        }
        if(Math.cos(heading)>.8) range=Math.min(range,Math.max(0,7-x-.63));
        return range;
    }
    @Override public synchronized Sensors read(long now) {
        advance(now);
        Sensors s=new Sensors(now,battery,frontRange(),emergency,bumper,cliff,guardOpen,full||deposited>=20,permit,
                false,intake,now<dropUntil,dynamics==null?.15+Math.abs(output.left()):leftCurrent,dynamics==null?.15+Math.abs(output.right()):rightCurrent,
                dynamics==null?(output.enabled()&&output.brush()>.1?(jam?2.6:.9):0):brushCurrent,
                dynamics==null?(output.enabled()&&output.belt()>.1?(jam?2.6:.6):0):beltCurrent,
                Math.round(leftDistance/(2*Math.PI*.1)*2048),Math.round(rightDistance/(2*Math.PI*.1)*2048));
        return dynamics==null?s:dynamics.readSensors(now,s);
    }
    @Override public synchronized void apply(Actuation a,long now) { output=a;lastApply=now; }
    public synchronized Vision oracle(long now) {
        Litter chosen=null;double best=99,bearing=0;
        for(Litter p:litter) if(p.stage==0) {
            double[] r=relative(p.x,p.y);double distance=Math.hypot(r[0],r[1]),angle=Math.atan2(r[1],r[0]);
            if(r[0]>.25 && Math.abs(angle)<.85 && distance<best && distance<3.3) { chosen=p;best=distance;bearing=angle; }
        }
        if(chosen==null) return new Vision(now,true,Label.NONE,.99,99,0,0,0);
        double v=clamp((best-.63-.22)*.7,0,.20)*Math.max(0,Math.cos(bearing));
        return new Vision(now,true,chosen.label,.98,Math.max(0,best-.63),bearing,v,clamp(bearing*2,-1,1));
    }
    @Override public synchronized Frame capture() { return new Frame(System.nanoTime(),camera()); }
    @Override public synchronized Vision infer(Frame f) { return oracle(f.timeNanos()); }
    public synchronized int deposited() { return deposited; }
    public synchronized Actuation actuation() { return output; }
    public synchronized List<Litter> objects() { return List.copyOf(litter); }
    /** Fixture configuration: a hazard can physically be picked up if the controller is wrong. */
    public synchronized void replaceLitter(List<Litter> objects) {
        litter.clear();litter.addAll(objects);deposited=0;intake=false;dropUntil=0;
    }
    public synchronized double[] pose() { return new double[]{x,y,heading}; }
    private static Color color(Label l) { return switch(l) {
        case PAPER->new Color(225,223,203);case PLASTIC_BOTTLE->new Color(60,159,191);case METAL_CAN->new Color(170,180,190);
        case HAZARD->new Color(190,48,60);default->Color.GRAY;
    }; }
    public synchronized BufferedImage camera() {
        BufferedImage image=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(168,176,175));g.fillRect(0,0,640,360);g.setColor(new Color(203,209,205));g.fillRect(0,0,640,55);
        g.setColor(new Color(149,157,155));for(int yy=70;yy<360;yy+=45) g.drawLine(0,yy,640,yy);
        List<Litter> copy=new ArrayList<>(litter);copy.sort((a,b)->Double.compare(Math.hypot(b.x-x,b.y-y),Math.hypot(a.x-x,a.y-y)));
        for(Litter p:copy) if(p.stage==0) {
            double[] r=relative(p.x,p.y);double h=.58,pitch=.55;
            double depth=r[0]*Math.cos(pitch)+h*Math.sin(pitch);
            if(depth<.2) continue;
            int px=(int)(320-380*r[1]/depth),py=(int)(180+380*(h*Math.cos(pitch)-r[0]*Math.sin(pitch))/depth);
            int w=(int)Math.max(5,55/depth),hh=(int)Math.max(5,(p.label==Label.PAPER?20:65)/depth);
            g.setColor(color(p.label));
            if(p.label==Label.PAPER) g.fillPolygon(new int[]{px-w/2,px+w/2,px+w/3,px-w/2},new int[]{py-hh,py-hh/2,py,py-3},4);
            else if(p.label==Label.PLASTIC_BOTTLE) { g.fillRoundRect(px-w/3,py-hh,w*2/3,hh,10,10);g.fillRect(px-w/6,py-hh-8,w/3,10); }
            else { g.fillRoundRect(px-w/2,py-hh,w,hh,8,8); }
            g.setColor(new Color(75,80,78));g.drawLine(px-w/2,py+1,px+w/2,py+1);
        }
        g.dispose();return image;
    }
    public synchronized BufferedImage mapImage(int width,int height) {
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(new Color(245,246,245));g.fillRect(0,0,width,height);double scale=Math.min(width/9.,height/5.5);
        java.util.function.Function<double[],Point> screen=a->new Point((int)((a[0]+1.2)*scale),(int)((2.7-a[1])*scale));
        g.setColor(new Color(215,220,216));for(int xx=-1;xx<=7;xx++){Point a=screen.apply(new double[]{xx,-2.5}),b=screen.apply(new double[]{xx,2.5});g.drawLine(a.x,a.y,b.x,b.y);}
        for(Litter p:litter) if(p.stage==0) {Point q=screen.apply(new double[]{p.x,p.y});g.setColor(color(p.label));g.fillOval(q.x-7,q.y-7,14,14);g.setColor(Color.DARK_GRAY);g.drawString(p.label.name(),q.x+10,q.y);}
        Point o=screen.apply(new double[]{4.7,1.25});g.setColor(Color.GRAY);g.fillOval(o.x-(int)(.4*scale),o.y-(int)(.4*scale),(int)(.8*scale),(int)(.8*scale));
        Polygon body=new Polygon();for(double[] c:new double[][]{{-.18,-.28},{.63,-.28},{.63,.28},{-.18,.28}}){Point q=screen.apply(new double[]{x+c[0]*Math.cos(heading)-c[1]*Math.sin(heading),y+c[0]*Math.sin(heading)+c[1]*Math.cos(heading)});body.addPoint(q.x,q.y);}
        g.setColor(new Color(24,92,101));g.fillPolygon(body);Point q=screen.apply(new double[]{x,y}),f=screen.apply(new double[]{x+.7*Math.cos(heading),y+.7*Math.sin(heading)});g.setColor(Color.BLACK);g.drawLine(q.x,q.y,f.x,f.y);
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));g.drawString(dynamicsName()+" / ORACLE_NOT_ML / deposited: "+deposited,15,height-18);g.dispose();return image;
    }
    @Override public synchronized void close() {
        if(closed)return;output=Actuation.off();closed=true;
        if(dynamics!=null)dynamics.close();
    }
}
