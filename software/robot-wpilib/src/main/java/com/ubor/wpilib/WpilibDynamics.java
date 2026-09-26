package com.ubor.wpilib;

import com.ubor.app.SimDynamics;
import static com.ubor.core.Types.*;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.system.plant.LinearSystemId;
import edu.wpi.first.wpilibj.simulation.*;

/** Real WPILib class calls. There is no fallback to the first-order model. */
public final class WpilibDynamics implements SimDynamics {
    public static final String BACKEND="WPILIB_2026.2.1";
    private final WpiParameters p;
    private final DCMotor driveMotor,brushMotor,beltMotor;
    private final DifferentialDrivetrainSim drive;
    private final DCMotorSim brush,belt;
    private final WpiSensors sensors;
    private State snapshot;
    private boolean closed;
    private double brushAngle,beltAngle;
    private double leftVolts,rightVolts,brushVolts,beltVolts,supplyCurrent;
    public WpilibDynamics(){this(WpiParameters.defaults());}
    public WpilibDynamics(WpiParameters parameters){
        p=parameters;WpiRuntime.acquire();
        try {
            driveMotor=motor(p.driveStallTorque(),p.driveStallAmps(),p.driveFreeAmps(),p.driveFreeRpm());
            brushMotor=motor(p.brushStallTorque(),p.brushStallAmps(),.1,p.brushFreeRpm());
            beltMotor=motor(p.beltStallTorque(),p.beltStallAmps(),.1,p.beltFreeRpm());
            drive=new DifferentialDrivetrainSim(driveMotor,p.driveReduction(),p.yawInertia(),p.massKg(),
                p.wheelRadius(),p.trackWidth(),null);
            brush=new DCMotorSim(LinearSystemId.createDCMotorSystem(brushMotor,p.brushInertia(),p.brushReduction()),brushMotor);
            belt=new DCMotorSim(LinearSystemId.createDCMotorSystem(beltMotor,p.beltInertia(),p.beltReduction()),beltMotor);
            sensors=new WpiSensors();
            snapshot=new State(0,0,0,0,0,0,0,0,0,p.batteryOpenVolts(),0,0,0,0);
            sensors.updateMotion(snapshot);
        } catch(RuntimeException|Error e){WpiRuntime.release();throw e;}
    }
    private DCMotor motor(double torque,double stall,double free,double rpm){
        return new DCMotor(p.motorNominalVolts(),torque,stall,free,rpm*2*Math.PI/60,1);
    }
    @Override public String name(){return BACKEND;}
    @Override public synchronized State state(){return snapshot;}
    public synchronized double gyroRadians(){return sensors.headingRadians();}
    public synchronized double halLeftDistance(){return sensors.leftDistance();}
    public synchronized double[] appliedVoltages(){return new double[]{leftVolts,rightVolts,brushVolts,beltVolts};}
    public synchronized double supplyCurrent(){return supplyCurrent;}
    public Pose2d pose(){State s=state();return new Pose2d(s.x(),s.y(),new edu.wpi.first.math.geometry.Rotation2d(s.heading()));}
    @Override public synchronized State step(double dt,Actuation request,boolean circuitClosed,boolean jammed){
        if(closed)throw new IllegalStateException("Closed physics");
        if(!Double.isFinite(dt)||dt<=0||dt>.05)throw new IllegalArgumentException("Physics step must be in (0,0.05] seconds");
        boolean powered=circuitClosed&&request.enabled();
        double bus=snapshot.batteryV();
        RoboRioSim.setVInVoltage(bus);
        leftVolts=powered?request.left()*bus:0;rightVolts=powered?request.right()*bus:0;
        brushVolts=powered?request.brush()*bus:0;beltVolts=powered?request.belt()*bus:0;
        // Zero voltage in a DC motor model means a SHORTED winding, not an open contactor.
        // For an open circuit cancel most back-EMF damping and retain assumed passive drag.
        // These effective inputs are mathematical drag terms, NOT energized physical outputs.
        double uL=powered?leftVolts:coastInput(driveMotor,snapshot.leftVelocity()/p.wheelRadius()*p.driveReduction());
        double uR=powered?rightVolts:coastInput(driveMotor,snapshot.rightVelocity()/p.wheelRadius()*p.driveReduction());
        double uB=powered?brushVolts:coastInput(brushMotor,snapshot.brushOmega()*p.brushReduction());
        double uT=powered?beltVolts:coastInput(beltMotor,snapshot.beltSpeed()/p.rollerRadius()*p.beltReduction());
        drive.setInputs(uL,uR);drive.update(dt);
        brush.setInputVoltage(uB);belt.setInputVoltage(uT);
        if(jammed){
            // Locked output shaft. WPILib supplies electrical motor constants, not contact forces.
            brush.setState(brushAngle,0);belt.setState(beltAngle,0);
        } else {
            brush.update(dt);belt.update(dt);
            brushAngle=brush.getAngularPositionRad();beltAngle=belt.getAngularPositionRad();
        }
        double omegaBrush=jammed?0:brush.getAngularVelocityRadPerSec();
        double omegaBelt=jammed?0:belt.getAngularVelocityRadPerSec();
        double omegaL=drive.getLeftVelocityMetersPerSecond()/p.wheelRadius()*p.driveReduction();
        double omegaR=drive.getRightVelocityMetersPerSecond()/p.wheelRadius()*p.driveReduction();
        // Sensor currents are winding current magnitudes. Supply uses PWM-average positive power.
        double iL=powered?driveMotor.getCurrent(omegaL,leftVolts):0;
        double iR=powered?driveMotor.getCurrent(omegaR,rightVolts):0;
        double iB=powered?brushMotor.getCurrent(omegaBrush*p.brushReduction(),brushVolts):0;
        double iT=powered?beltMotor.getCurrent(omegaBelt*p.beltReduction(),beltVolts):0;
        supplyCurrent=p.logicCurrent()+draw(request.left(),iL)+draw(request.right(),iR)+draw(request.brush(),iB)+draw(request.belt(),iT);
        // No SOC/chemistry or regenerative overvoltage model; reverse power is not added to the bus.
        double loaded=BatterySim.calculateLoadedBatteryVoltage(p.batteryOpenVolts(),p.batteryResistance(),supplyCurrent);
        Pose2d pose=drive.getPose();
        snapshot=new State(pose.getX(),pose.getY(),drive.getHeading().getRadians(),
            drive.getLeftVelocityMetersPerSecond(),drive.getRightVelocityMetersPerSecond(),
            drive.getLeftPositionMeters(),drive.getRightPositionMeters(),omegaBrush,omegaBelt*p.rollerRadius(),loaded,
            Math.abs(iL),Math.abs(iR),Math.abs(iB),Math.abs(iT));
        sensors.updateMotion(snapshot);return snapshot;
    }
    private double coastInput(DCMotor motor,double shaftOmega){return motor.getVoltage(0,shaftOmega)*(1-p.coastDragFraction());}
    private static double draw(double duty,double windingAmps){return Math.max(0,duty*windingAmps);}
    @Override public synchronized Sensors readSensors(long now,Sensors worldSensors){return sensors.roundTrip(now,worldSensors);}
    @Override public synchronized void close(){if(!closed){closed=true;try{sensors.close();}finally{WpiRuntime.release();}}}
}
