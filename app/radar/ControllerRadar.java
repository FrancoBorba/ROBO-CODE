package app.radar;

import java.awt.Color;
import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;
import java.awt.Graphics2D;

public class ControllerRadar {
    
    private final AdvancedRobot robot;
    
    // Coordenadas Atuais
    private double enemyPositionX = -1;
    private double enemyPositionY = -1;
    
    // Coordenadas Preditivas (Futuras)
    private double predictedX = -1;
    private double predictedY = -1;
    
    private boolean findEnemy = false;

    public ControllerRadar(AdvancedRobot robot) {
        this.robot = robot;
    }

    /**
     * Executa a lógica do Predictive Perfect Lock (Trava Preditiva Perfeita)
     */
    public void update(ScannedRobotEvent e) {
        //  Coordenadas exatas do inimigo no frame atual
        double absoluteBearing = robot.getHeadingRadians() + e.getBearingRadians();
        this.enemyPositionX = robot.getX() + Math.sin(absoluteBearing) * e.getDistance();
        this.enemyPositionY = robot.getY() + Math.cos(absoluteBearing) * e.getDistance();
        

        //  Simula onde o inimigo estará no próximo frame (Tick + 1)
        double enemyHeading = e.getHeadingRadians();
        double enemyVelocity = e.getVelocity();
        
        this.predictedX = enemyPositionX + Math.sin(enemyHeading) * enemyVelocity;
        this.predictedY = enemyPositionY + Math.cos(enemyHeading) * enemyVelocity;
        
        //  Calcula o ângulo para a posição FUTURA
        double anguloFuturo = Math.atan2(predictedX - robot.getX(), predictedY - robot.getY());
        
        //  O PERFECT LOCK (Substituindo o antigo 2.0)
        double radarTurn = Utils.normalRelativeAngle(anguloFuturo - robot.getRadarHeadingRadians());
        
        // Calcula qual é a grossura visual do tanque (36 pixels) a partir da nossa distância
        // Isso cria um arco de varredura microscópico e perfeito, colado no chassi do inimigo
        double fatorSweep = Math.signum(radarTurn) * Utils.normalRelativeAngle(Math.atan(36.0 / e.getDistance())); 
        
        // Adicionamos um multiplicador leve (1.2) para garantir o movimento e somamos o micro-arco de segurança
        robot.setTurnRadarRightRadians((radarTurn * 1.2) + fatorSweep);

        this.findEnemy = true;
    }

    /**
     * Desenha elementos visuais na tela para a Apresentação e Depuração
     */
    public void render(Graphics2D g) {
        if (!findEnemy) return;

        // ------------------------------------------
        // VISUALIZAÇÃO DO PRESENTE
        // ------------------------------------------
        // Linha Verde: Nossa posição até o inimigo atual
        g.setColor(Color.GREEN);
        g.drawLine((int) robot.getX(), (int) robot.getY(), (int) enemyPositionX, (int) enemyPositionY);

        // Alvo Vermelho: Posição atual e centro do inimigo
        g.setColor(Color.RED);
        g.drawOval((int) enemyPositionX - 25, (int) enemyPositionY - 25, 50, 50);
        g.fillOval((int) enemyPositionX - 4, (int) enemyPositionY - 4, 8, 8);
        
        // ------------------------------------------
        // VISUALIZAÇÃO DO FUTURO 
        // ------------------------------------------
        // Desenha um alvo Amarelo mostrando onde o radar ESTÁ MIRANDO (Onde o inimigo vai estar)
        g.setColor(Color.YELLOW);
        g.drawOval((int) predictedX - 10, (int) predictedY - 10, 20, 20);
        
        // Desenha uma linha ligando o presente ao futuro (Vetor de Velocidade)
        g.drawLine((int) enemyPositionX, (int) enemyPositionY, (int) predictedX, (int) predictedY);
    }
}