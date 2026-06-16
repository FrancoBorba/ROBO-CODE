package app.movement;

import java.awt.Color;

import app.enemy.EnemyWave;
import app.enemy.EnemyWaveTracker;
import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class EvasionManager {
    
    private final AdvancedRobot robot;
    private final EnemyWaveTracker waveTracker;
    
    // System memory
    private int moveDirection = 1; 

    // The "database" for the ML system (31 memory buckets)
    private static int[] dangerStats = new int[31];

    public EvasionManager(AdvancedRobot robot, EnemyWaveTracker waveTracker) {
        this.robot = robot;
        this.waveTracker = waveTracker;
    }

    public void update(ScannedRobotEvent e) {
        double absoluteEnemyAngle = robot.getHeadingRadians() + e.getBearingRadians();

        // SURF: use the closest wave detected by the shared module.
        EnemyWave closestWave = waveTracker.getClosestWave();

        if (closestWave != null) {
            double dangerForward = predictDanger(closestWave, 1);
            double dangerBackward = predictDanger(closestWave, -1);

            if (dangerForward < dangerBackward) {
                moveDirection = 1;
            } else {
                moveDirection = -1;
            }
        }

        // CONTROLE DE DISTÂNCIA DINÂMICO
        double distancia = e.getDistance();
        double anguloAtaque = Math.PI / 2; // Começa assumindo 90 graus (Órbita lateral perfeita)
        
        // Mantém o inimigo eternamente na faixa de 350 a 450 pixels!
        if (distancia > 450) {
            anguloAtaque -= 0.4; // Fecha a curva para perseguir o inimigo
        } else if (distancia < 350) {
            anguloAtaque += 0.4; // Abre a curva para fugir de inimigos kamikazes
        }

        // APPLY MOVEMENT AND WALL SMOOTHING
        double desiredAngle = absoluteEnemyAngle + (anguloAtaque * moveDirection); 
        
        // Call the wall-smoothing helper
        desiredAngle = applyWallSmoothing(robot.getX(), robot.getY(), desiredAngle, moveDirection); 
        
        robot.setTurnRightRadians(Utils.normalRelativeAngle(desiredAngle - robot.getHeadingRadians()));
        
        // Passa a velocidade máxima e garante o movimento
        robot.setMaxVelocity(8.0);
        robot.setAhead(100 * moveDirection);
    }

    // MÉTODOS DO SURF

    private double predictDanger(EnemyWave wave, int testDirection) {
        double testAngle = wave.directAngle + (Math.PI / 2) * testDirection;
        double futureX = robot.getX() + Math.sin(testAngle) * 150 * testDirection;
        double futureY = robot.getY() + Math.cos(testAngle) * 150 * testDirection;
        int dangerIndex = getStatIndex(wave, futureX, futureY);
        return dangerStats[dangerIndex];
    }

    public void registerDamage(HitByBulletEvent e) {
        robot.setBodyColor(Color.RED); 
        
        EnemyWave hitWave = waveTracker.getClosestWave();

        if (hitWave != null) {
            double damageX = e.getBullet().getX();
            double damageY = e.getBullet().getY();
            
            int punishedIndex = getStatIndex(hitWave, damageX, damageY);
            dangerStats[punishedIndex]++;
            
            System.out.println(String.format(
                "[IA SURFER] Risk in zone %d increased to %d",
                punishedIndex,
                dangerStats[punishedIndex]
            ));

            waveTracker.removeWave(hitWave);
        }
    }

    private int getStatIndex(EnemyWave wave, double x, double y) {
        double angleToTarget = Utils.normalAbsoluteAngle(Math.atan2(x - wave.originX, y - wave.originY));
        double angleDiff = Utils.normalRelativeAngle(angleToTarget - wave.directAngle);
        double maxEscapeAngle = Math.asin(8.0 / wave.bulletSpeed);
        double guessFactor = angleDiff / maxEscapeAngle;
        int index = (int) Math.round((guessFactor * 15) + 15);
        return Math.max(0, Math.min(30, index));
    }

    // ALGORITMO DE WALL SMOOTHING 
    private double applyWallSmoothing(double currentX, double currentY, double desiredAngle, int moveOrientation) {
        // Margin: 18 pixels tank + braking space
        double margin = 55.0; 
        double stick = 160.0; // "rear sensor" distance
        double fieldW = robot.getBattleFieldWidth();
        double fieldH = robot.getBattleFieldHeight();

        int loopProtector = 0;

        // If the future point hits the wall, curve the trajectory by 0.1 radians per iteration
        while (!isTrajectorySafe(currentX + Math.sin(desiredAngle) * stick * moveOrientation, 
                                 currentY + Math.cos(desiredAngle) * stick * moveOrientation, 
                                 fieldW, fieldH, margin) && loopProtector < 100) {
            // Turning in the direction of movement makes the robot slide along the edge
            desiredAngle += moveOrientation * 0.1; 
            loopProtector++;
        }
        return desiredAngle;
    }

    private boolean isTrajectorySafe(double testX, double testY, double width, double height, double margin) {
        return testX > margin && testX < width - margin && testY > margin && testY < height - margin;
    }
}
