package app;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import app.radar.ControllerRadar; // Importa o seu módulo de radar
import java.awt.Graphics2D;

public class App extends AdvancedRobot {
    
    private ControllerRadar radar;

    @Override
    public void run() {
       
        // Initialize the radar
        radar = new ControllerRadar(this);

      
        setAdjustRadarForGunTurn(true); // The radar does not follow the gun
        setAdjustGunForRobotTurn(true); // THe gun does not follow the tanl

        // Turn to right until find the enemy
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

        // Loop principal limpo (State Machine)
        while (true) {
            // O método scan() é exigido pelo Multiplier Lock para manter o radar ativo
            scan();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {
        // Alimenta o seu radar com os dados do inimigo escaneado
        radar.update(event);

        // (No futuro, suas classes de tiro e movimento também serão chamadas aqui)
    }

    @Override
    public void onPaint(Graphics2D g) {
        // Desenha a linha verde e o alvo vermelho na tela do jogo
        if (radar != null) {
            radar.render(g);
        }
    }
}