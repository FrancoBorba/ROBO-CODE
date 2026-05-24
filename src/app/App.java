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

        // atira quando encontra inimigo
        fire(1.5);
    }
    
}
