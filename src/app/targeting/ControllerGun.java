package app.targeting;

import app.defense.DefenseStrategyManager;
import app.enemy.EnemyWave;
import app.enemy.EnemyWaveTracker;
import robocode.AdvancedRobot;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ControllerGun {

    private static final double SHIELD_POWER = 0.1;
    private static final double MAX_GUN_ERROR = Math.toRadians(2.0);
    private static final double MAX_SHIELD_GUN_TURN = Math.toRadians(45.0); 
    private static final double MIN_ENEMY_BULLET_POWER_TO_SHIELD = 0.5; 

    private final AdvancedRobot robot;
    private final EnemyWaveTracker waveTracker;
    private final DefenseStrategyManager defense;

    // 🧠 BANCO DE DADOS SEGMENTADO COM DECAIMENTO (DOUBLE)
    private static Map<String, double[][][]> gunStats = new HashMap<>();
    
    private ArrayList<GunWave> activeWaves = new ArrayList<>();

    public ControllerGun(AdvancedRobot robot, EnemyWaveTracker waveTracker, DefenseStrategyManager defense) {
        this.robot = robot;
        this.waveTracker = waveTracker;
        this.defense = defense;
    }

    private double[] getStats(String enemyName, double distance, double velocity) {
        if (!gunStats.containsKey(enemyName)) {
            gunStats.put(enemyName, new double[5][3][31]); 
        }
        int distIdx = Math.min(4, (int)(distance / 150.0));
        int velIdx = Math.min(2, (int)(Math.abs(velocity) / 3.0));
        return gunStats.get(enemyName)[distIdx][velIdx];
    }

    public void update(ScannedRobotEvent e) {
        update(e, calcularPotenciaAtaque(e));
    }

    public void update(ScannedRobotEvent e, double firePower) {
        atualizarOndasDeTiro(e);
        
        double potenciaReal = normalizarPotencia(firePower, e);
        criarOndaVirtual(e, potenciaReal);

        if (defense.isShieldEnabled() && tentarBulletShield()) {
            return;
        }

        atacarInimigo(e, potenciaReal);
    }

    private void criarOndaVirtual(ScannedRobotEvent e, double firePower) {
        double absoluteBearing = robot.getHeadingRadians() + e.getBearingRadians();
        GunWave novaOnda = new GunWave();
        novaOnda.origemX = robot.getX();
        novaOnda.origemY = robot.getY();
        novaOnda.directAngle = absoluteBearing;
        novaOnda.bulletSpeed = Rules.getBulletSpeed(firePower);
        novaOnda.fireTime = robot.getTime();
        novaOnda.fireDistance = e.getDistance();
        novaOnda.fireVelocity = e.getVelocity();
        
        double enemyHeading = e.getHeadingRadians();
        novaOnda.lateralDirection = (Math.sin(enemyHeading - absoluteBearing) * novaOnda.fireVelocity < 0) ? -1 : 1;
        
        activeWaves.add(novaOnda);
    }

    private void atualizarOndasDeTiro(ScannedRobotEvent e) {
        double absoluteBearing = robot.getHeadingRadians() + e.getBearingRadians();
        double enemyX = robot.getX() + Math.sin(absoluteBearing) * e.getDistance();
        double enemyY = robot.getY() + Math.cos(absoluteBearing) * e.getDistance();

        for (int i = 0; i < activeWaves.size(); i++) {
            GunWave onda = activeWaves.get(i);
            double distPercorrida = (robot.getTime() - onda.fireTime) * onda.bulletSpeed;
            double distAteInimigo = Math.hypot(enemyX - onda.origemX, enemyY - onda.origemY);

            if (distPercorrida > distAteInimigo - 18) {
                double anguloAteInimigo = Utils.normalAbsoluteAngle(Math.atan2(enemyX - onda.origemX, enemyY - onda.origemY));
                double diferencaAngulo = Utils.normalRelativeAngle(anguloAteInimigo - onda.directAngle);
                double anguloMaxEscape = Math.asin(8.0 / onda.bulletSpeed);
                
                double gf = (diferencaAngulo / anguloMaxEscape) * onda.lateralDirection;
                int index = (int) Math.round((gf * 15) + 15);
                index = Math.max(0, Math.min(30, index));
                
                // ALGORITMO DO ESQUECIMENTO (FLATTENER)
                double[] stats = getStats(e.getName(), onda.fireDistance, onda.fireVelocity);
                for (int j = 0; j < stats.length; j++) {
                    stats[j] *= 0.85; 
                }
                stats[index] += 1.0;
                
                activeWaves.remove(i);
                i--;
            }
        }
    }

    private void atacarInimigo(ScannedRobotEvent e, double firePower) {
        double aimAngle = calcularAnguloDeMira(e, firePower);
        double gunTurn = Utils.normalRelativeAngle(aimAngle - robot.getGunHeadingRadians());

        robot.setTurnGunRightRadians(gunTurn);

        if (robot.getGunHeat() == 0 && firePower >= Rules.MIN_BULLET_POWER && robot.getEnergy() > firePower && Math.abs(gunTurn) < MAX_GUN_ERROR) {
            robot.setFireBullet(firePower);
        }
    }

    private double calcularAnguloDeMira(ScannedRobotEvent e, double firePower) {
        if (e.getDistance() < 120 || Math.abs(e.getVelocity()) < 0.2) {
            return robot.getHeadingRadians() + e.getBearingRadians();
        }
        return calcularMiraGuessFactor(e, firePower);
    }

    private double calcularMiraGuessFactor(ScannedRobotEvent e, double firePower) {
        double[] stats = getStats(e.getName(), e.getDistance(), e.getVelocity());
        int melhorIndice = 15; 
        double maiorFrequencia = -1.0;
        
        for (int i = 0; i < stats.length; i++) {
            if (stats[i] > maiorFrequencia) {
                maiorFrequencia = stats[i];
                melhorIndice = i;
            }
        }

        double gf = (melhorIndice - 15) / 15.0;
        double bulletSpeed = Rules.getBulletSpeed(firePower);
        double maxEscape = Math.asin(8.0 / bulletSpeed);
        double absoluteBearing = robot.getHeadingRadians() + e.getBearingRadians();
        
        int lateralDirection = 1;
        double enemyVelocity = e.getVelocity();
        if (enemyVelocity != 0) {
            double enemyHeading = e.getHeadingRadians();
            lateralDirection = (Math.sin(enemyHeading - absoluteBearing) * enemyVelocity < 0) ? -1 : 1;
        }

        return absoluteBearing + (gf * maxEscape * lateralDirection);
    }

    private boolean tentarBulletShield() {
        EnemyWave wave = waveTracker.getClosestWave();
        if (wave == null || robot.getEnergy() <= SHIELD_POWER + 0.2) return false;
        if (wave.bulletPower < MIN_ENEMY_BULLET_POWER_TO_SHIELD) return false;

        double tempoDecorrido = robot.getTime() - wave.fireTime;
        double currentBulletX = wave.originX + Math.sin(wave.directAngle) * (tempoDecorrido * wave.bulletSpeed);
        double currentBulletY = wave.originY + Math.cos(wave.directAngle) * (tempoDecorrido * wave.bulletSpeed);

        double distToBullet = Math.hypot(robot.getX() - currentBulletX, robot.getY() - currentBulletY);
        double ourBulletSpeed = Rules.getBulletSpeed(SHIELD_POWER); 
        double interceptionTime = distToBullet / (ourBulletSpeed + wave.bulletSpeed); 
        
        double futureBulletX = currentBulletX + Math.sin(wave.directAngle) * (wave.bulletSpeed * interceptionTime);
        double futureBulletY = currentBulletY + Math.cos(wave.directAngle) * (wave.bulletSpeed * interceptionTime);

        double targetAngle = Math.atan2(futureBulletX - robot.getX(), futureBulletY - robot.getY());
        double gunTurn = Utils.normalRelativeAngle(targetAngle - robot.getGunHeadingRadians());

        if (Math.abs(gunTurn) > MAX_SHIELD_GUN_TURN) return false;
        robot.setTurnGunRightRadians(gunTurn);

        double ticksToAlign = Math.ceil(Math.abs(Math.toDegrees(gunTurn)) / 20.0);
        double ticksToCool = Math.ceil(robot.getGunHeat() / robot.getGunCoolingRate());
        long fireTick = robot.getTime() + (long) Math.max(ticksToAlign, ticksToCool);
        
        if (wave.getDistanceTraveled(fireTick) >= (Math.hypot(robot.getX() - wave.originX, robot.getY() - wave.originY) - 18.0)) return false;

        if (robot.getGunHeat() == 0.0 && Math.abs(gunTurn) < Math.toRadians(1.0)) {
            robot.setFireBullet(SHIELD_POWER);
            defense.registerShieldAttempt();
            return true;
        }
        return false;
    }

    private double calcularPotenciaAtaque(ScannedRobotEvent e) {
        double distancia = e.getDistance();
        double velocidade = Math.abs(e.getVelocity());
        double potencia;

        if (distancia < 150) potencia = 3.0;
        else if (distancia > 300 && velocidade > 4.0) potencia = 1.9; // Smart Firepower: Velocidade > Dano
        else potencia = 2.4 - (distancia / 1000.0);

        if (robot.getEnergy() < 30) potencia = Math.min(potencia, robot.getEnergy() / 15.0);
        if (e.getEnergy() < 4.0) potencia = Math.min(potencia, Math.max(Rules.MIN_BULLET_POWER, e.getEnergy() / 4.0));

        return normalizarPotencia(potencia, e);
    }

    private double normalizarPotencia(double potencia, ScannedRobotEvent e) {
        return Math.min(Math.min(potencia, Rules.MAX_BULLET_POWER), Math.max(Rules.MIN_BULLET_POWER, robot.getEnergy() - 0.1));
    }

    class GunWave {
        double origemX, origemY;
        long fireTime;
        double bulletSpeed;
        double directAngle;
        int lateralDirection; 
        double fireDistance; 
        double fireVelocity; 
    }
}