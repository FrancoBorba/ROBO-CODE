package app.enemy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import robocode.AdvancedRobot;
import robocode.BulletHitBulletEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

/**
 * Centraliza a detecção de tiros inimigos.
 *
 * Este módulo é usado tanto pelo movimento quanto pelo ataque. Assim, a mesma
 * informação de bala inimiga pode alimentar o wave surfing e o bullet shield.
 */
public class EnemyWaveTracker {
    private static final double MAX_WAVE_MARGIN = 60.0;

    private final AdvancedRobot robot;
    private final List<EnemyWave> waves = new ArrayList<>();
    private double previousEnemyEnergy = 100.0;

    public EnemyWaveTracker(AdvancedRobot robot) {
        this.robot = robot;
    }

    public void update(ScannedRobotEvent e) {
        double currentEnemyEnergy = e.getEnergy();
        double energyDrop = previousEnemyEnergy - currentEnemyEnergy;

        double absoluteBearing = robot.getHeadingRadians() + e.getBearingRadians();
        double currentEnemyX = robot.getX() + Math.sin(absoluteBearing) * e.getDistance();
        double currentEnemyY = robot.getY() + Math.cos(absoluteBearing) * e.getDistance();

        if (isEnemyFire(energyDrop)) {
          
            double enemyHeading = e.getHeadingRadians();
            double enemyVelocity = e.getVelocity();
            
            double realOriginX = currentEnemyX - (Math.sin(enemyHeading) * enemyVelocity);
            double realOriginY = currentEnemyY - (Math.cos(enemyHeading) * enemyVelocity);
            
            // O ângulo exato e verdadeiro da bala vindo na nossa direção
            double realDirectAngle = Utils.normalAbsoluteAngle(Math.atan2(robot.getX() - realOriginX, robot.getY() - realOriginY));

            EnemyWave wave = new EnemyWave(
                realOriginX,
                realOriginY,
                robot.getTime() - 1, // Compensação de tempo (Perfeito)
                energyDrop,
                Rules.getBulletSpeed(energyDrop),
                realDirectAngle,      // Compensação espacial aplicada
                e.getDistance(),
                robot.getVelocity()
            );

            waves.add(wave);
            robot.setBodyColor(java.awt.Color.MAGENTA);
        }

        removeOldWaves();
        previousEnemyEnergy = currentEnemyEnergy;
    }

    public EnemyWave getClosestWave() {
        EnemyWave closest = null;
        double closestRemainingDistance = Double.POSITIVE_INFINITY;

        for (EnemyWave wave : waves) {
            double remainingDistance = wave.getRemainingDistanceTo(
                robot.getX(), robot.getY(), robot.getTime()
            );

            if (remainingDistance > 0 && remainingDistance < closestRemainingDistance) {
                closestRemainingDistance = remainingDistance;
                closest = wave;
            }
        }

        return closest;
    }

    public List<EnemyWave> getWaves() {
        return Collections.unmodifiableList(waves);
    }

    public void removeWave(EnemyWave wave) {
        waves.remove(wave);
    }

    public void onBulletHitBullet(BulletHitBulletEvent event) {
        EnemyWave closest = getClosestWave();

        if (closest != null) {
            removeWave(closest);
        }
    }

    private boolean isEnemyFire(double energyDrop) {
        return energyDrop >= Rules.MIN_BULLET_POWER && energyDrop <= Rules.MAX_BULLET_POWER;
    }

    private void removeOldWaves() {
        for (int i = 0; i < waves.size(); i++) {
            EnemyWave wave = waves.get(i);

            double distanceToRobot = wave.getDistanceTo(robot.getX(), robot.getY());
            double traveledDistance = wave.getDistanceTraveled(robot.getTime());

            if (traveledDistance > distanceToRobot + MAX_WAVE_MARGIN) {
                waves.remove(i);
                i--;
            }
        }
    }
}
