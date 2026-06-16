package app.defense;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import robocode.AdvancedRobot;

/**
 * Máquina de estados entre Bullet Shield (defesa por interceptação) e WaveSurfing
 * (movimentação evasiva).
 *
 * Estado padrão: Bullet Shield habilitado (estratégia primária).
 *
 * Se o escudo falhar demais em uma janela curta de tempo (muitos tiros recebidos
 * ou muita energia perdida), o escudo é desativado temporariamente. Nesse modo de
 * fallback, o ControllerGun deixa de tentar interceptar balas e passa a atacar o
 * inimigo normalmente; a defesa do robô passa a depender inteiramente do WaveSurfing
 * do EvasionManager, que já roda em todo tick de onScannedRobot.
 *
 * Depois de um cooldown (ou no início do próximo round, já que esta classe é
 * recriada a cada chamada de run()), o robô tenta reabilitar o escudo automaticamente.
 */
public class DefenseStrategyManager {

    // ---- Parâmetros de decisão  ----

    // Janela de tempo (em ticks) usada para avaliar a eficiência recente do escudo.
    private static final long WINDOW_TICKS = 40;

    // X: quantidade de tiros recebidos dentro da janela que já é considerada falha do escudo.
    private static final int MAX_IMPACTS_WINDOW = 2;

    // Y: pontos de energia perdidos dentro da janela (equivalente a % da energia cheia de 100)
    // que também é considerada falha do escudo.
    private static final double MAX_ENERGY_LOST_WINDOW = 18.0;

    // Tempo de espera (ticks) em modo fallback antes de tentar religar o Bullet Shield.
    private static final long COOLDOWN_RETURN_TICKS = 60;

    private final AdvancedRobot robot;
    private final List<Impact> recentImpacts = new ArrayList<>();

    private boolean shieldEnabled = true;
    private long lastFailureTick = -1;

    // Metrics for debugging / on-screen display (onPaint).
    private int shieldAttempts = 0;
    private int successfulIntercepts = 0;

    private static class Impact {
        final long tick;
        final double energyLost;

        Impact(long tick, double energyLost) {
            this.tick = tick;
            this.energyLost = energyLost;
        }
    }

    public DefenseStrategyManager(AdvancedRobot robot) {
        this.robot = robot;
    }

    /**
     * Consultado pelo ControllerGun antes de decidir se tenta o Bullet Shield.
     * Esta é a leitura do estado atual da máquina de estados; também é o ponto em
     * que verificamos se o cooldown de retorno já passou.
     */
    public boolean isShieldEnabled() {
        tryReenableShield();
        return shieldEnabled;
    }

    /** Chamado pelo ControllerGun sempre que de fato tenta interceptar uma bala (não só consulta). */
    public void registerShieldAttempt() {
        shieldAttempts++;
    }

    /** Chamado pelo App.onBulletHitBullet quando o escudo intercepta com sucesso uma bala inimiga. */
    public void registerSuccessfulIntercept() {
        successfulIntercepts++;
    }

    /**
     * Chamado pelo App.onHitByBullet sempre que o robô é atingido.
     * Atualiza a janela de impactos e decide se o escudo deve ser desativado.
     *
     * @param energiaPerdida normalmente event.getDamage() do HitByBulletEvent.
     */
    public void registerImpact(double energyLost) {
        long now = robot.getTime();
        recentImpacts.add(new Impact(now, energyLost));
        removeOldImpacts(now);
        evaluateShieldFailure(now);
    }

    private void evaluateShieldFailure(long now) {
        if (!shieldEnabled) {
            return; // already in fallback mode, nothing to re-evaluate
        }

        int impactCount = recentImpacts.size();
        double totalEnergyLost = 0;
        for (Impact impact : recentImpacts) {
            totalEnergyLost += impact.energyLost;
        }

        boolean failedByCount = impactCount >= MAX_IMPACTS_WINDOW;
        boolean failedByEnergy = totalEnergyLost >= MAX_ENERGY_LOST_WINDOW;

        if (failedByCount || failedByEnergy) {
            shieldEnabled = false;
            lastFailureTick = now;
            System.out.println(String.format(
                "[defense] Bullet Shield disabled -> fallback to WaveSurfing. "
                + "Impacts in window: %d | Energy lost in window: %.1f",
                impactCount, totalEnergyLost
            ));
        }
    }

    private void tryReenableShield() {
        if (!shieldEnabled && (robot.getTime() - lastFailureTick) >= COOLDOWN_RETURN_TICKS) {
            shieldEnabled = true;
            recentImpacts.clear();
            System.out.println("[defense] Cooldown finished: re-enabling Bullet Shield.");
        }
    }

    private void removeOldImpacts(long now) {
        Iterator<Impact> it = recentImpacts.iterator();
        while (it.hasNext()) {
            Impact impact = it.next();
            if (now - impact.tick > WINDOW_TICKS) {
                it.remove();
            }
        }
    }

    /** Usado pelo App.onPaint para mostrar o estado da defense em tela. */
    public String getStatistics() {
        double successRate = shieldAttempts == 0 ? 0 : (100.0 * successfulIntercepts / shieldAttempts);
        return String.format("Shield: %s | Attempts: %d | Successes: %d (%.1f%%)",
            shieldEnabled ? "ENABLED" : "FALLBACK", shieldAttempts, successfulIntercepts, successRate);
    }
}
