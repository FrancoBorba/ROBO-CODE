package app.movement;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import app.enemy.EnemyWave;
import app.enemy.EnemyWaveTracker;
import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class EvasionManager {
    
    private final AdvancedRobot robot;
    private final EnemyWaveTracker waveTracker;

    private int currentDirection = 1;

private static Map<String, double[][][]> enemyStats = new HashMap<>();
    public EvasionManager(AdvancedRobot robot, EnemyWaveTracker waveTracker) {
        this.robot = robot;
        this.waveTracker = waveTracker;
 
    }

private double[] getStatsForEnemy(String enemyName, double distance, double velocity) {
        if (!enemyStats.containsKey(enemyName)) {
            enemyStats.put(enemyName, new double[5][3][31]); // Matriz de double
        }
        int distIdx = Math.min(4, (int)(distance / 150.0));
        int velIdx = Math.min(2, (int)(Math.abs(velocity) / 3.0));
        return enemyStats.get(enemyName)[distIdx][velIdx];
    }

    public void update(ScannedRobotEvent e) {
        EnemyWave closestWave = waveTracker.getClosestWave();
        String enemyName = e.getName();



        robot.setMaxVelocity(8.0); // Libera o motor em potência máxima

        // 1. DECISÃO DE ÓRBITA (Surfing)
        if (closestWave != null) {
            // Avalia o risco simulando a física para frente e para trás
            double dangerForward = predictDanger(closestWave, 1, enemyName);
            double dangerBackward = predictDanger(closestWave, -1, enemyName);

            // Adiciona uma inércia para evitar que o robô fique tremendo parado
            if (dangerForward < dangerBackward) {
                currentDirection = 1;
            } else if (dangerBackward < dangerForward) {
                currentDirection = -1;
            }
        }

        // 2. MOVIMENTO ORBITAL
        double absoluteEnemyAngle = robot.getHeadingRadians() + e.getBearingRadians();
        double distanceToEnemy = e.getDistance();
        
        // Ajusta o ângulo para manter a distância perfeita (fecha se longe, abre se perto)
        double orbitAngle = (Math.PI / 2) + (distanceToEnemy > 400 ? -0.2 : 0.2); 
        double moveAngle = absoluteEnemyAngle + (orbitAngle * currentDirection);

        moveAngle = applyWallSmoothing(robot.getX(), robot.getY(), moveAngle, currentDirection);

        double turnAngle = Utils.normalRelativeAngle(moveAngle - robot.getHeadingRadians());
        
        // Engata a ré se for mais rápido do que virar o tanque inteiro
        double moveSpeed = 100;
        if (Math.abs(turnAngle) > Math.PI / 2) {
            turnAngle = Utils.normalRelativeAngle(turnAngle + Math.PI);
            moveSpeed = -100;
        }

        robot.setTurnRightRadians(turnAngle);
        robot.setAhead(moveSpeed);
    }

    // ==========================================
    // 🧠 FÍSICA PREDITIVA (A MÁGICA DA ELITE)
    // ==========================================
  // =========================================================
    // ALGORITMO ORIGINAL DO BASICSURFER (SIMULAÇÃO TICK-A-TICK)
    // =========================================================
    private double predictDanger(EnemyWave surfWave, int direction, String enemyName) {
        
        // Começamos a simulação a partir do nosso estado ATUAL
        double predictedX = robot.getX();
        double predictedY = robot.getY();
        double predictedVelocity = robot.getVelocity();
        double predictedHeading = robot.getHeadingRadians();
        
        double maxTurning, moveAngle, moveDir;
        int counter = 0; // Quantidade de Ticks no futuro
        boolean intercepted = false;

        // O Loop simula o tempo passando (1 iteração = 1 tick do Robocode)
        do {
            // 1. Calcula o ângulo orbital com base na posição simulada
            double absoluteEnemyAngle = Math.atan2(surfWave.originX - predictedX, surfWave.originY - predictedY);
            
            // O WallSmoothing entra aqui para entortar a órbita na simulação se a parede estiver perto
            double desiredAngle = applyWallSmoothing(predictedX, predictedY, absoluteEnemyAngle + (Math.PI / 2 * direction), direction);
            
            moveAngle = Utils.normalRelativeAngle(desiredAngle - predictedHeading);
            moveDir = 1;

            // 2. Se a curva for muito brusca (maior que 90 graus), é melhor engatar a ré e frear
            if (Math.cos(moveAngle) < 0) {
                moveAngle = Utils.normalRelativeAngle(moveAngle + Math.PI);
                moveDir = -1;
            }

            moveAngle = Utils.normalRelativeAngle(moveAngle);

            // 3. Simula a Rotação do Tanque (Máx 10 graus por tick, menos se estiver muito rápido)
            maxTurning = Math.PI / 18.0 - (Math.PI / 240.0) * Math.abs(predictedVelocity);
            
            if (moveAngle < 0) {
                predictedHeading -= Math.min(maxTurning, -moveAngle);
            } else {
                predictedHeading += Math.min(maxTurning, moveAngle);
            }

            // 4. Simula a Aceleração e Frenagem reais do motor
            if (predictedVelocity * moveDir < 0) {
                predictedVelocity += 2.0 * moveDir; // Freio tem força 2.0
            } else {
                predictedVelocity += 1.0 * moveDir; // Aceleração tem força 1.0
            }

            // Limita a velocidade máxima
            predictedVelocity = Math.max(-8.0, Math.min(8.0, predictedVelocity));

            // 5. Move o tanque 1 tick para a frente na simulação
            predictedX += Math.sin(predictedHeading) * predictedVelocity;
            predictedY += Math.cos(predictedHeading) * predictedVelocity;

            counter++;

            // 6. Calcula se a bala inimiga encostou na nossa blindagem (18 pixels) neste tick
            double waveDistanceTraveled = (robot.getTime() - surfWave.fireTime + counter) * surfWave.bulletSpeed;
            double distanceToSimulatedBot = Math.hypot(predictedX - surfWave.originX, predictedY - surfWave.originY);
            
            if (waveDistanceTraveled > distanceToSimulatedBot - 18.0) {
                intercepted = true; // A bala bateu! Acabou a simulação.
            }

        // Fim de uma iteração (Se não bateu, e a simulação não travou, calcula o próximo tick)
        } while (!intercepted && counter < 500);


    int dangerIndex = getStatIndex(surfWave, predictedX, predictedY);
    return getStatsForEnemy(enemyName, surfWave.fireDistance, surfWave.fireVelocity)[dangerIndex];
    }

    public void registerDamage(HitByBulletEvent e) {
        robot.setBodyColor(Color.RED); 
        EnemyWave hitWave = waveTracker.getClosestWave();

        if (hitWave != null) {
            int punishedIndex = getStatIndex(hitWave, robot.getX(), robot.getY());
            double[] stats = getStatsForEnemy(e.getName(), hitWave.fireDistance, hitWave.fireVelocity);
    
            for (int i = 0; i < stats.length; i++) {
                stats[i] *= 0.85; 
            }
            stats[punishedIndex] += 1.0; 
            
            waveTracker.removeWave(hitWave);
        }
    }

    public int getStatIndex(EnemyWave wave, double x, double y) {
        double angleToTarget = Utils.normalAbsoluteAngle(Math.atan2(x - wave.originX, y - wave.originY));
        double angleDiff = Utils.normalRelativeAngle(angleToTarget - wave.directAngle);
        double maxEscapeAngle = Math.asin(8.0 / wave.bulletSpeed);
        double guessFactor = angleDiff / maxEscapeAngle;
        int index = (int) Math.round((guessFactor * 15) + 15);
        return Math.max(0, Math.min(30, index));
    }

    private double applyWallSmoothing(double currentX, double currentY, double desiredAngle, int moveOrientation) {
        double margin = 55.0; 
        double stick = 160.0; 
        double fieldW = robot.getBattleFieldWidth();
        double fieldH = robot.getBattleFieldHeight();

        int loopProtector = 0;
        while (!isTrajectorySafe(currentX + Math.sin(desiredAngle) * stick, 
                                 currentY + Math.cos(desiredAngle) * stick, 
                                 fieldW, fieldH, margin) && loopProtector < 100) {
            desiredAngle += moveOrientation * 0.1; 
            loopProtector++;
        }
        return desiredAngle;
    }

    private boolean isTrajectorySafe(double testX, double testY, double width, double height, double margin) {
        return testX > margin && testX < width - margin && testY > margin && testY < height - margin;
    }
}