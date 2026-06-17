package app.targeting;

import app.defense.DefenseStrategyManager;
import app.enemy.EnemyWave;
import app.enemy.EnemyWaveTracker;
import robocode.AdvancedRobot;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

/**
 * Controla o ataque do robô.
 *
 * Estratégia atual:
 * 1. Se o Bullet Shield estiver habilitado pela DefenseStrategyManager, tenta interceptar
 *    apenas quando a onda inimiga é realmente interceptável.
 * 2. Caso contrário (escudo em fallback, onda inválida ou tiro fraco), usa ataque normal
 *    com potência dinâmica.
 * 3. O ataque normal usa mira direta para alvos muito próximos/parados e mira linear
 *    para inimigos em movimento.
 */
public class ControllerGun {

    private static final double SHIELD_POWER = 0.1;
    private static final double MAX_GUN_ERROR = Math.toRadians(2.0);

    // Faixa em que o shield costuma ser mais viável. Muito perto: não dá tempo.
    // Muito longe: a previsão da bala inimiga fica pouco confiável.
    private static final double MIN_SHIELD_DISTANCE = 80.0;
    private static final double MAX_SHIELD_DISTANCE = 240.0;

    // Evita que o bullet shield roube a mira quando a arma teria que girar demais.
    private static final double MAX_SHIELD_GUN_TURN = Math.toRadians(25.0);

    // Só vale priorizar shield contra tiros minimamente relevantes.
    // Contra tiros muito fracos, geralmente é melhor continuar atacando.
    private static final double MIN_ENEMY_BULLET_POWER_TO_SHIELD = 1.0;

    private static final double ROBOT_HALF_SIZE = 18.0;
    private static final int MAX_PREDICTION_TICKS = 80;

    private final AdvancedRobot robot;
    private final EnemyWaveTracker waveTracker;
    private final DefenseStrategyManager defense;

    public ControllerGun(AdvancedRobot robot, EnemyWaveTracker waveTracker, DefenseStrategyManager defense) {
        this.robot = robot;
        this.waveTracker = waveTracker;
        this.defense = defense;
    }

    /**
     * Método principal do ataque.
     *
     * Prioridade:
     * 1. Se o Bullet Shield estiver habilitado e houver uma bala inimiga perigosa,
     *    tentar interceptá-la.
     * 2. Caso contrário, usar o ataque normal contra o robô inimigo.
     */
    public void update(ScannedRobotEvent e) {
        if (defense.isShieldEnabled() && tentarBulletShield()) {
            return;
        }

        atacarInimigo(e, calcularPotenciaAtaque(e));
    }

    /**
     * Mantém compatibilidade com a assinatura antiga usada pelo App anterior.
     */
    public void update(ScannedRobotEvent e, double firePower) {
        if (defense.isShieldEnabled() && tentarBulletShield()) {
            return;
        }

        atacarInimigo(e, normalizarPotencia(firePower, e));
    }

    /**
     * Tenta interceptar uma bala inimiga com uma bala nossa.
     *
     * Retorna true somente quando o shield assumiu prioridade da arma.
     * Se a situação não for boa para shield, retorna false e libera o ataque normal.
     */
    private boolean tentarBulletShield() {
        EnemyWave wave = waveTracker.getClosestWave();

        if (wave == null || robot.getEnergy() <= SHIELD_POWER + 0.2) {
            return false;
        }

        if (wave.bulletPower < MIN_ENEMY_BULLET_POWER_TO_SHIELD) {
            return false;
        }

        double remainingDistance = wave.getRemainingDistanceTo(
            robot.getX(), robot.getY(), robot.getTime()
        );

        if (remainingDistance < MIN_SHIELD_DISTANCE || remainingDistance > MAX_SHIELD_DISTANCE) {
            return false;
        }

        double targetAngle = calcularAnguloInterceptacaoBala(wave);
        double gunTurn = Utils.normalRelativeAngle(targetAngle - robot.getGunHeadingRadians());

        if (Math.abs(gunTurn) > MAX_SHIELD_GUN_TURN) {
            return false;
        }

        defense.registerShieldAttempt();

        robot.setTurnGunRightRadians(gunTurn);

        if (robot.getGunHeat() == 0 && Math.abs(gunTurn) < MAX_GUN_ERROR) {
            robot.setFireBullet(SHIELD_POWER);
            System.out.println("[BULLET SHIELD] Disparo defensivo executado.");
        }

        return true;
    }

    private double calcularAnguloInterceptacaoBala(EnemyWave wave) {
        double enemyBulletDistance = wave.getDistanceTraveled(robot.getTime());
        double remainingDistance = wave.getRemainingDistanceTo(
            robot.getX(), robot.getY(), robot.getTime()
        );

        double ourBulletSpeed = Rules.getBulletSpeed(SHIELD_POWER);

        // As balas se aproximam uma da outra. Esta é uma aproximação simples,
        // suficiente para um primeiro bullet shield competitivo.
        double ticksUntilIntercept = remainingDistance / (wave.bulletSpeed + ourBulletSpeed);
        double enemyFutureDistance = enemyBulletDistance + (wave.bulletSpeed * ticksUntilIntercept);

        double targetX = wave.originX + Math.sin(wave.directAngle) * enemyFutureDistance;
        double targetY = wave.originY + Math.cos(wave.directAngle) * enemyFutureDistance;

        return Math.atan2(targetX - robot.getX(), targetY - robot.getY());
    }

    private void atacarInimigo(ScannedRobotEvent e, double firePower) {
        double aimAngle = calcularAnguloDeMira(e, firePower);
        double gunTurn = Utils.normalRelativeAngle(aimAngle - robot.getGunHeadingRadians());

        robot.setTurnGunRightRadians(gunTurn);

        if (robot.getGunHeat() == 0
                && firePower >= Rules.MIN_BULLET_POWER
                && robot.getEnergy() > firePower
                && Math.abs(gunTurn) < MAX_GUN_ERROR) {
            robot.setFireBullet(firePower);
        }
    }

    /**
     * Escolhe a mira:
     * - mira direta quando o inimigo está muito perto ou praticamente parado;
     * - mira linear quando o inimigo está em movimento.
     */
    private double calcularAnguloDeMira(ScannedRobotEvent e, double firePower) {
        if (e.getDistance() < 120 || Math.abs(e.getVelocity()) < 0.2) {
            return calcularMiraDireta(e);
        }

        return calcularMiraLinear(e, firePower);
    }

    private double calcularMiraDireta(ScannedRobotEvent e) {
        return robot.getHeadingRadians() + e.getBearingRadians();
    }

    /**
     * Linear targeting: prevê onde o inimigo estará quando a bala chegar,
     * assumindo que ele manterá heading e velocidade atuais.
     */
    private double calcularMiraLinear(ScannedRobotEvent e, double firePower) {
        double absoluteBearing = robot.getHeadingRadians() + e.getBearingRadians();
        double bulletSpeed = Rules.getBulletSpeed(firePower);

        double predictedX = robot.getX() + Math.sin(absoluteBearing) * e.getDistance();
        double predictedY = robot.getY() + Math.cos(absoluteBearing) * e.getDistance();

        double enemyHeading = e.getHeadingRadians();
        double enemyVelocity = e.getVelocity();

        int tick = 0;

        while ((++tick) * bulletSpeed < distancia(robot.getX(), robot.getY(), predictedX, predictedY)
                && tick < MAX_PREDICTION_TICKS) {
            predictedX += Math.sin(enemyHeading) * enemyVelocity;
            predictedY += Math.cos(enemyHeading) * enemyVelocity;

            predictedX = limitar(predictedX, ROBOT_HALF_SIZE, robot.getBattleFieldWidth() - ROBOT_HALF_SIZE);
            predictedY = limitar(predictedY, ROBOT_HALF_SIZE, robot.getBattleFieldHeight() - ROBOT_HALF_SIZE);
        }

        return Math.atan2(predictedX - robot.getX(), predictedY - robot.getY());
    }

    /**
     * Potência dinâmica para equilibrar dano e gasto de energia.
     */
    private double calcularPotenciaAtaque(ScannedRobotEvent e) {
        double distancia = e.getDistance();
        double potencia;

        if (distancia < 150) {
            potencia = 3.0;
        } else if (distancia < 300) {
            potencia = 2.2;
        } else if (distancia < 500) {
            potencia = 1.5;
        } else {
            potencia = 1.0;
        }

        // Conserva energia quando estamos em situação ruim.
        if (robot.getEnergy() < 10) {
            potencia = Math.min(potencia, 0.7);
        } else if (robot.getEnergy() < 20) {
            potencia = Math.min(potencia, 1.2);
        }

        // Evita gastar potência alta para finalizar inimigo com pouca energia.
        if (e.getEnergy() < 4.0) {
            potencia = Math.min(potencia, Math.max(Rules.MIN_BULLET_POWER, e.getEnergy() / 4.0));
        }

        return normalizarPotencia(potencia, e);
    }

    private double normalizarPotencia(double potencia, ScannedRobotEvent e) {
        double limitePorEnergia = Math.max(Rules.MIN_BULLET_POWER, robot.getEnergy() - 0.1);
        double limitePorInimigo = e.getEnergy() > 0 ? Math.max(Rules.MIN_BULLET_POWER, e.getEnergy()) : Rules.MIN_BULLET_POWER;

        double normalizada = Math.min(potencia, Rules.MAX_BULLET_POWER);
        normalizada = Math.min(normalizada, limitePorEnergia);
        normalizada = Math.min(normalizada, limitePorInimigo);

        return limitar(normalizada, Rules.MIN_BULLET_POWER, Rules.MAX_BULLET_POWER);
    }

    private double limitar(double valor, double minimo, double maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }

    private double distancia(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }
}
