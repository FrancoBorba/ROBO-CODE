package app.targeting;

import app.enemy.EnemyWave;
import app.enemy.EnemyWaveTracker;
import robocode.AdvancedRobot;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class ControllerGun {
    
    private static final double SHIELD_POWER = 0.1;
    private static final double MAX_GUN_ERROR = Math.toRadians(2.0);
    private static final double MIN_SHIELD_DISTANCE = 60.0;
    private static final double MAX_SHIELD_DISTANCE = 280.0;

    private final AdvancedRobot robot;
    private final EnemyWaveTracker waveTracker;

    public ControllerGun(AdvancedRobot robot, EnemyWaveTracker waveTracker) {
        this.robot = robot;
        this.waveTracker = waveTracker;
    }

    /**
     * Método principal do ataque.
     *
     * Prioridade:
     * 1. Tentar bullet shield quando houver uma bala inimiga perigosa.
     * 2. Caso contrário, usar o ataque normal contra o robô inimigo.
     */
    public void update(ScannedRobotEvent e) {
        if (tentarBulletShield()) {
            return;
        }

        atacarInimigo(e, calcularPotenciaAtaque(e));
    }

    /**
     * Mantém compatibilidade com a assinatura antiga usada pelo App anterior.
     */
    public void update(ScannedRobotEvent e, double firePower) {
        if (tentarBulletShield()) {
            return;
        }

        atacarInimigo(e, firePower);
    }

    private boolean tentarBulletShield() {
        EnemyWave wave = waveTracker.getClosestWave();

        if (wave == null || robot.getEnergy() <= SHIELD_POWER) {
            return false;
        }

        double enemyBulletDistance = wave.getDistanceTraveled(robot.getTime());
        double remainingDistance = wave.getRemainingDistanceTo(
            robot.getX(), robot.getY(), robot.getTime()
        );

        if (remainingDistance < MIN_SHIELD_DISTANCE || remainingDistance > MAX_SHIELD_DISTANCE) {
            return false;
        }

        double ourBulletSpeed = Rules.getBulletSpeed(SHIELD_POWER);
        double ticksUntilIntercept = remainingDistance / (wave.bulletSpeed + ourBulletSpeed);
        double enemyFutureDistance = enemyBulletDistance + (wave.bulletSpeed * ticksUntilIntercept);

        double targetX = wave.originX + Math.sin(wave.directAngle) * enemyFutureDistance;
        double targetY = wave.originY + Math.cos(wave.directAngle) * enemyFutureDistance;

        double aimAngle = Math.atan2(targetX - robot.getX(), targetY - robot.getY());
        double gunTurn = Utils.normalRelativeAngle(aimAngle - robot.getGunHeadingRadians());

        robot.setTurnGunRightRadians(gunTurn);

        if (robot.getGunHeat() == 0 && Math.abs(gunTurn) < MAX_GUN_ERROR) {
            robot.setFireBullet(SHIELD_POWER);
            System.out.println("[BULLET SHIELD] Disparo defensivo executado.");
        }

        return true;
    }

    private void atacarInimigo(ScannedRobotEvent e, double firePower) {
        // Calcula o ângulo absoluto do inimigo em radianos.
        double absoluteBearing = robot.getHeadingRadians() + e.getBearingRadians();
        
        // Calcula a diferença para a arma e normaliza.
        double gunTurn = Utils.normalRelativeAngle(absoluteBearing - robot.getGunHeadingRadians());
        
        // Manda a arma girar pelo caminho mais curto.
        robot.setTurnGunRightRadians(gunTurn);
        
        if (robot.getGunHeat() == 0
                && firePower > 0
                && robot.getEnergy() > firePower
                && Math.abs(gunTurn) < MAX_GUN_ERROR) {
            robot.setFire(firePower);
        }
    }

    private double calcularPotenciaAtaque(ScannedRobotEvent e) {
        boolean modoAtaqueAtivo = e.getEnergy() < (robot.getEnergy() / 2.0) || e.getDistance() < 150;
        return modoAtaqueAtivo ? 3.0 : 0.1;
    }
}
