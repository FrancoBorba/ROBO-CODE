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
    
    // Memória do sistema
    private int direcaoMovel = 1; 

    // O "Banco de Dados" do Aprendizado de Máquina (31 blocos de memória)
    private static int[] estatisticasPerigo = new int[31];

    public EvasionManager(AdvancedRobot robot, EnemyWaveTracker waveTracker) {
        this.robot = robot;
        this.waveTracker = waveTracker;
    }

    public void update(ScannedRobotEvent e) {
        double anguloAbsolutoInimigo = robot.getHeadingRadians() + e.getBearingRadians();

        // O SURF: usa a onda mais próxima detectada pelo módulo compartilhado.
        EnemyWave ondaMaisProxima = waveTracker.getClosestWave();

        if (ondaMaisProxima != null) {
            double perigoFrente = preverPerigo(ondaMaisProxima, 1);
            double perigoTras = preverPerigo(ondaMaisProxima, -1);

            if (perigoFrente < perigoTras) {
                direcaoMovel = 1;
            } else {
                direcaoMovel = -1;
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

        // APLICA A MOVIMENTAÇÃO E O WALL SMOOTHING
        double anguloDesejado = anguloAbsolutoInimigo + (anguloAtaque * direcaoMovel); 
        
        // Chama a função blindada contra batidas na parede
        anguloDesejado = aplicarWallSmoothing(robot.getX(), robot.getY(), anguloDesejado, direcaoMovel); 
        
        robot.setTurnRightRadians(Utils.normalRelativeAngle(anguloDesejado - robot.getHeadingRadians()));
        
        // Passa a velocidade máxima e garante o movimento
        robot.setMaxVelocity(8.0);
        robot.setAhead(100 * direcaoMovel);
    }

    // MÉTODOS DO SURF

    private double preverPerigo(EnemyWave onda, int direcaoTestada) {
        double anguloTeste = onda.directAngle + (Math.PI / 2) * direcaoTestada;
        double xFuturo = robot.getX() + Math.sin(anguloTeste) * 150 * direcaoTestada;
        double yFuturo = robot.getY() + Math.cos(anguloTeste) * 150 * direcaoTestada;
        int indicePerigo = obterIndiceEstatistico(onda, xFuturo, yFuturo);
        return estatisticasPerigo[indicePerigo];
    }

    public void registrarDano(HitByBulletEvent e) {
        robot.setBodyColor(Color.RED); 
        
        EnemyWave ondaQueAcertou = waveTracker.getClosestWave();

        if (ondaQueAcertou != null) {
            double xDano = e.getBullet().getX();
            double yDano = e.getBullet().getY();
            
            int indicePunido = obterIndiceEstatistico(ondaQueAcertou, xDano, yDano);
            estatisticasPerigo[indicePunido]++;
            
            System.out.println(String.format(
                "[IA SURFER] Risco na zona %d subiu para %d",
                indicePunido,
                estatisticasPerigo[indicePunido]
            ));

            waveTracker.removeWave(ondaQueAcertou);
        }
    }

    private int obterIndiceEstatistico(EnemyWave onda, double x, double y) {
        double anguloAteAlvo = Utils.normalAbsoluteAngle(Math.atan2(x - onda.originX, y - onda.originY));
        double diferencaAngulo = Utils.normalRelativeAngle(anguloAteAlvo - onda.directAngle);
        double anguloMaximoEscape = Math.asin(8.0 / onda.bulletSpeed);
        double guessFactor = diferencaAngulo / anguloMaximoEscape;
        int indice = (int) Math.round((guessFactor * 15) + 15);
        return Math.max(0, Math.min(30, indice));
    }

    // ALGORITMO DE WALL SMOOTHING 
    private double aplicarWallSmoothing(double xAtual, double yAtual, double anguloDesejado, int orientacaoMovel) {
        // Margem: 18 pixels do tanque + espaço de frenagem
        double margem = 55.0; 
        double stick = 160.0; // Distância do "sensor de ré"
        double campoL = robot.getBattleFieldWidth();
        double campoA = robot.getBattleFieldHeight();

        int loopProtector = 0;

        // Se o ponto futuro bater na parede, vai curvando a trajetória de 0.1 em 0.1 radianos
        while (!trajetoriaSegura(xAtual + Math.sin(anguloDesejado) * stick * orientacaoMovel, 
                                 yAtual + Math.cos(anguloDesejado) * stick * orientacaoMovel, 
                                 campoL, campoA, margem) && loopProtector < 100) {
            // Girar a favor da orientação de movimento faz o robô deslizar na borda
            anguloDesejado += orientacaoMovel * 0.1; 
            loopProtector++;
        }
        return anguloDesejado;
    }

    private boolean trajetoriaSegura(double xTest, double yTest, double L, double A, double margem) {
        return xTest > margem && xTest < L - margem && yTest > margem && yTest < A - margem;
    }
}
