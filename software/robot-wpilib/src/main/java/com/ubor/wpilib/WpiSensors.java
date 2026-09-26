package com.ubor.wpilib;

import com.ubor.app.SimDynamics;
import com.ubor.core.DriveControl;
import static com.ubor.core.Types.*;
import edu.wpi.first.wpilibj.*;
import edu.wpi.first.wpilibj.simulation.*;

/** Virtual channels only, NOT Raspberry Pi BCM pin numbers and NOT SPI chip emulation. */
final class WpiSensors implements AutoCloseable {
    private final Encoder left=new Encoder(0,1),right=new Encoder(2,3);
    private final EncoderSim leftSim=new EncoderSim(left),rightSim=new EncoderSim(right);
    private final AnalogGyro gyro=new AnalogGyro(0,0,0.0); // preset center, no blocking calibration
    private final AnalogGyroSim gyroSim=new AnalogGyroSim(gyro);
    // AI1 range: 1 V/m. AI2..5 current: 0.5 V/A. Saturation is explicitly modelled.
    private final AnalogInput range=new AnalogInput(1);
    private final AnalogInputSim rangeSim=new AnalogInputSim(range);
    private final AnalogInput[] current=new AnalogInput[4];
    private final AnalogInputSim[] currentSim=new AnalogInputSim[4];
    // DIO4..12: NC estop/bumper/cliff/cover, full, permit, driver fault, intake, drop.
    private final DigitalInput[] digital=new DigitalInput[9];
    private final DIOSim[] digitalSim=new DIOSim[9];
    private boolean closed;
    WpiSensors(){
        double perPulse=2*Math.PI*DriveControl.WHEEL_R_M/DriveControl.COUNTS_PER_TURN;
        left.setDistancePerPulse(perPulse);right.setDistancePerPulse(perPulse);
        for(int i=0;i<4;i++){current[i]=new AnalogInput(i+2);currentSim[i]=new AnalogInputSim(current[i]);}
        for(int i=0;i<9;i++){digital[i]=new DigitalInput(i+4);digitalSim[i]=new DIOSim(digital[i]);}
    }
    void updateMotion(SimDynamics.State s){
        leftSim.setDistance(s.leftPosition());rightSim.setDistance(s.rightPosition());
        leftSim.setRate(s.leftVelocity());rightSim.setRate(s.rightVelocity());
        // WPILib physics is CCW positive; AnalogGyro angle is CW positive.
        gyroSim.setAngle(-Math.toDegrees(s.heading()));
        gyroSim.setRate(-Math.toDegrees((s.rightVelocity()-s.leftVelocity())/DriveControl.TRACK_M));
        RoboRioSim.setVInVoltage(s.batteryV()); // 25.6 V model bus, not default 12 V FRC battery
        double[] amps={s.leftCurrent(),s.rightCurrent(),s.brushCurrent(),s.beltCurrent()};
        for(int i=0;i<4;i++)currentSim[i].setVoltage(clamp(amps[i]*.5,0,5));
    }
    Sensors roundTrip(long now,Sensors s){
        rangeSim.setVoltage(clamp(s.frontRangeM(),0,5));
        boolean[] levels={!s.emergency(),!s.bumper(),!s.cliff(),!s.coverOpen(),s.hopperFull(),
            s.hardwarePermit(),s.driverFault(),s.intakeBeam(),s.dropBeam()};
        for(int i=0;i<9;i++)digitalSim[i].setValue(levels[i]);
        return new Sensors(now,RobotController.getBatteryVoltage(),range.getVoltage(),
            !digital[0].get(),!digital[1].get(),!digital[2].get(),!digital[3].get(),digital[4].get(),digital[5].get(),
            digital[6].get(),digital[7].get(),digital[8].get(),
            current[0].getVoltage()*2,current[1].getVoltage()*2,current[2].getVoltage()*2,current[3].getVoltage()*2,
            left.get(),right.get());
    }
    double headingRadians(){return gyro.getRotation2d().getRadians();}
    double leftDistance(){return left.getDistance();}
    double rightDistance(){return right.getDistance();}
    @Override public void close(){
        if(closed)return;closed=true;
        for(DigitalInput d:digital)if(d!=null)d.close();
        for(AnalogInput a:current)if(a!=null)a.close();
        range.close();gyro.close();right.close();left.close();
    }
}
