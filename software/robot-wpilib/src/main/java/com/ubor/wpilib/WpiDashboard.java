package com.ubor.wpilib;

import com.ubor.app.Simulator;
import com.ubor.app.SimDynamics;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/** Optional read-only telemetry. NetworkTables is NOT the motor command transport. */
public final class WpiDashboard implements AutoCloseable {
    private final NetworkTableInstance nt=NetworkTableInstance.getDefault();
    private final Field2d field=new Field2d();
    public WpiDashboard(){
        // Explicit loopback binding. No telemetry service is exposed to the Internet by default.
        nt.startServer("ubor-nt-persist.json","127.0.0.1",1735,5810);
        SmartDashboard.putData("UBOR/Field",field);
        SmartDashboard.putString("UBOR/Perception","SIM_ORACLE_NOT_ML");
    }
    public void update(Simulator world,WpilibDynamics physics){
        SimDynamics.State s=physics.state();field.setRobotPose(physics.pose());
        SmartDashboard.putNumber("UBOR/BatteryV",s.batteryV());
        SmartDashboard.putNumber("UBOR/SupplyA",physics.supplyCurrent());
        SmartDashboard.putNumber("UBOR/LeftMps",s.leftVelocity());
        SmartDashboard.putNumber("UBOR/RightMps",s.rightVelocity());
        SmartDashboard.putNumber("UBOR/BrushRadS",s.brushOmega());
        SmartDashboard.putNumber("UBOR/BeltMps",s.beltSpeed());
        SmartDashboard.putNumber("UBOR/Collected",world.deposited());
        SmartDashboard.updateValues();nt.flush();
    }
    @Override public void close(){field.close();nt.stopServer();}
}
