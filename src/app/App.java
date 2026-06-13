package app;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import robocode.HitByBulletEvent; 
import app.radar.ControllerRadar;
import app.targeting.ControllerGun;
import app.movement.EvasionManager; 
import java.awt.Graphics2D;

public class App extends AdvancedRobot {
    
    private ControllerRadar radar;
    private ControllerGun gun;
    private EvasionManager movimento; // Nova variável

    @Override
    public void run() {
        radar = new ControllerRadar(this);
        gun = new ControllerGun(this);
        movimento = new EvasionManager(this); // Instanciando

        setAdjustRadarForGunTurn(true);
        setAdjustGunForRobotTurn(true);
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

        while (true) {
            scan();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {
        // 1. Radar trava no inimigo
        radar.update(event);
        
        // 2. A arma calcula e atira (O mestre do Ataque agora tem liberdade total)
        // Aqui já implementamos a transição de Ataque/Defesa que você pediu!
        boolean modoAtaqueAtivo = event.getEnergy() < (getEnergy() / 2.0) || event.getDistance() < 150;
        double potencia = modoAtaqueAtivo ? 3.0 : 0.1; // Se tiver vantagem, bate forte. Senão, só sangra o inimigo.
        gun.update(event, potencia);
        
        // 3. As esteiras preparam a esquiva
        movimento.update(event);
    }

    @Override
    public void onHitByBullet(HitByBulletEvent event) {
        // Se formos atingidos, o módulo de movimento anota a falha para aprender
        movimento.registrarDano(event);
    }

    @Override
    public void onPaint(Graphics2D g) {
        if (radar != null) {
            radar.render(g);
        }
    }
}