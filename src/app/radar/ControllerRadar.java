package app.radar;

import java.awt.Color;
import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;
import java.awt.Graphics2D;

public class ControllerRadar {
    
    private final AdvancedRobot robot;
    
    // Iniital position start negative
    private double enemyPositionX = -1;
    private double enemyPositionY = -1;
    private boolean findEnemy = false;

    public ControllerRadar( AdvancedRobot robot) {
        this.robot = robot;
    }

    /**
     * Executa a lógica do Multiplier Lock (Fator 2.0)
     */
    public void update(ScannedRobotEvent e) {
        // 1. Algoritmo do Multiplier Lock
        double radarTurn = robot.getHeadingRadians() + e.getBearingRadians() - robot.getRadarHeadingRadians();
        double menorGiro = Utils.normalRelativeAngle(radarTurn);
        robot.setTurnRadarRightRadians(menorGiro * 2.0);

        // 2. Cálculo trigonométrico para descobrir a posição X e Y do inimigo na arena
        double absoluteBearing = robot.getHeadingRadians() + e.getBearingRadians();
        this.enemyPositionX = robot.getX() + Math.sin(absoluteBearing) * e.getDistance();
        this.enemyPositionY = robot.getY() + Math.cos(absoluteBearing) * e.getDistance();
        this.findEnemy = true;
    }

    /**
     * Desenha elementos visuais na tela para depuração
     */
    public void render(Graphics2D g) {
        if (!findEnemy) return;

        // Desenha uma linha verde do nosso robô até o inimigo
        g.setColor(Color.GREEN);
        g.drawLine((int) robot.getX(), (int) robot.getY(), (int) enemyPositionX, (int) enemyPositionY);

        // Desenha um alvo (círculo vazio) de 50x50 pixels exatamente em cima do inimigo
        g.setColor(Color.RED);
        g.drawOval((int) enemyPositionX - 25, (int) enemyPositionY - 25, 50, 50);
        
        // Desenha um ponto preenchido no centro do inimigo
        g.fillOval((int) enemyPositionX - 4, (int) enemyPositionY- 4, 8, 8);
    }
}
