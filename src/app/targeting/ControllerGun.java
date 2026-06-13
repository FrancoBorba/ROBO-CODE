package app.targeting;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class ControllerGun {
    
    private final AdvancedRobot robot;

    public ControllerGun(AdvancedRobot robot) {
        this.robot = robot;
    }

    public void update(ScannedRobotEvent e , double firePower) {
        //  Calcula o ângulo absoluto do inimigo em radianos
        double absoluteBearing = robot.getHeadingRadians() + e.getBearingRadians();
        
        //  Calcula a diferença para a arma e NORMALIZA 
        double gunTurn = Utils.normalRelativeAngle(absoluteBearing - robot.getGunHeadingRadians());
        
        //  Manda a arma girar pelo caminho mais curto
        robot.setTurnGunRightRadians(gunTurn);
        
        //  Só atira se a arma estiver quase alinhada (margem de erro de 2 graus)
        // Usamos Math.toRadians para converter os 2 graus para radianos e comparar

        if (firePower > 0 && Math.abs(gunTurn) < Math.toRadians(2.0)) {
            // Usamos setFire em vez de fire para não travar a execução do robô
            robot.setFire(firePower);
        }
    }
}