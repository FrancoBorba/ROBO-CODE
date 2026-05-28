package app;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;

public class App extends AdvancedRobot{
    
        @Override
    public void run() {

        // radar independente
        setAdjustRadarForGunTurn(true);

        // arma independente
        setAdjustGunForRobotTurn(true);

        while (true) {

            // movimentação
            setAhead(150);

            // virar
            setTurnRight(45);

            // radar girando
            setTurnRadarRight(360);

            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {

        // calcula quanto a arma precisa girar
        double giroArma = getHeading() + event.getBearing() - getGunHeading();

        // gira a arma na direção do inimigo
        setTurnGunRight(giroArma);

        // só atira se a arma já estiver quase alinhada
        if (Math.abs(giroArma) < 10) {
            setFire(1.5);
        }
    }
    
}
