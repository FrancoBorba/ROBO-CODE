package app;

import java.awt.Graphics2D;

import app.enemy.EnemyWaveTracker;
import app.movement.EvasionManager;
import app.radar.ControllerRadar;
import app.targeting.ControllerGun;
import robocode.AdvancedRobot;
import robocode.BulletHitBulletEvent;
import robocode.HitByBulletEvent;
import robocode.ScannedRobotEvent;

public class App extends AdvancedRobot {
    
    private ControllerRadar radar;
    private ControllerGun gun;
    private EvasionManager movimento;
    private EnemyWaveTracker waveTracker;

    @Override
    public void run() {
        waveTracker = new EnemyWaveTracker(this);
        radar = new ControllerRadar(this);
        gun = new ControllerGun(this, waveTracker);
        movimento = new EvasionManager(this, waveTracker);

        setAdjustRadarForGunTurn(true);
        setAdjustGunForRobotTurn(true);
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

        while (true) {
            scan();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {
        // 1. Centraliza a detecção dos tiros inimigos.
        waveTracker.update(event);

        // 2. Radar trava no inimigo.
        radar.update(event);
        
        // 3. A arma decide entre bullet shield e ataque normal.
        gun.update(event);
        
        // 4. O movimento usa as mesmas ondas para evasão.
        movimento.update(event);
    }

    @Override
    public void onHitByBullet(HitByBulletEvent event) {
        movimento.registrarDano(event);
    }

    @Override
    public void onBulletHitBullet(BulletHitBulletEvent event) {
        waveTracker.onBulletHitBullet(event);
        System.out.println("[BULLET SHIELD] Nossa bala colidiu com uma bala inimiga.");
    }

    @Override
    public void onPaint(Graphics2D g) {
        if (radar != null) {
            radar.render(g);
        }
    }
}
