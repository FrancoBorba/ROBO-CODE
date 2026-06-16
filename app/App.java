package app;
// Edit test: inserido comentário para verificar permissão de escrita

import java.awt.Color;
import java.awt.Graphics2D;

import app.defense.DefenseStrategyManager;
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
    private EvasionManager movement;
    private EnemyWaveTracker waveTracker;
    private DefenseStrategyManager defense;

    @Override
    public void run() {
        waveTracker = new EnemyWaveTracker(this);
        defense = new DefenseStrategyManager(this);
        radar = new ControllerRadar(this);
        gun = new ControllerGun(this, waveTracker, defense);
        movement = new EvasionManager(this, waveTracker);

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
        
        // 3. A arma decide entre bullet shield e ataque normal, consultando a DefenseStrategyManager.
        gun.update(event);
        
        // 4. O movimento usa as mesmas ondas para evasão (WaveSurfing, fallback natural do escudo).
        movement.update(event);
    }

    @Override
    public void onHitByBullet(HitByBulletEvent event) {
        movement.registerDamage(event);

        // Alimenta a máquina de estados de defesa com a energia perdida nesse impacto.
        defense.registerImpact(robocode.Rules.getBulletDamage(event.getPower()));
    }

    @Override
    public void onBulletHitBullet(BulletHitBulletEvent event) {
        waveTracker.onBulletHitBullet(event);

        // Interceptação bem-sucedida do Bullet Shield: conta como acerto.
        defense.registerSuccessfulIntercept();

        System.out.println("[BULLET SHIELD] Nossa bala colidiu com uma bala inimiga.");
    }

    @Override
    public void onPaint(Graphics2D g) {
        if (radar != null) {
            radar.render(g);
        }

        if (defense != null) {
            g.setColor(Color.WHITE);
            g.drawString(defense.getStatistics(), 10, 20);
        }
    }
}
