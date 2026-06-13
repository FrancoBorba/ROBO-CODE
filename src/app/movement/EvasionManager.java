package app.movement;

import java.awt.Color;
import java.util.ArrayList;

import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class EvasionManager {
    
    private final AdvancedRobot robot;
    
    // Memória do sistema
    private double energiaAnterior = 100.0;
    private int direcaoMovel = 1; 

    // O "Banco de Dados" do Aprendizado de Máquina (31 blocos de memória)
    private static int[] estatisticasPerigo = new int[31];
    
    private ArrayList<OndaInimiga> ondasAtivas = new ArrayList<>();

    public EvasionManager(AdvancedRobot robot) {
        this.robot = robot;
    }

    public void update(ScannedRobotEvent e) {
        double energiaAtual = e.getEnergy();
        double quedaEnergia = energiaAnterior - energiaAtual;
        
        double anguloAbsolutoInimigo = robot.getHeadingRadians() + e.getBearingRadians();
        double inimigoX = robot.getX() + Math.sin(anguloAbsolutoInimigo) * e.getDistance();
        double inimigoY = robot.getY() + Math.cos(anguloAbsolutoInimigo) * e.getDistance();

        //  DETECTOR DE TIRO 
        if (quedaEnergia > 0.0 && quedaEnergia <= 3.0) {
            robot.setBodyColor(Color.MAGENTA); 
            
            OndaInimiga novaOnda = new OndaInimiga();
            novaOnda.origemX = inimigoX;
            novaOnda.origemY = inimigoY;
            novaOnda.tempoCriacao = robot.getTime() - 1; 
            novaOnda.potenciaBala = quedaEnergia;
            novaOnda.velocidadeBala = 20 - (3 * quedaEnergia);
            novaOnda.anguloDireto = anguloAbsolutoInimigo + Math.PI; 
            
            ondasAtivas.add(novaOnda);
        }

        //  ATUALIZADOR DE ONDAS 
        atualizarOndas();

        //  O SURF (
        if (!ondasAtivas.isEmpty()) {
            OndaInimiga ondaMaisProxima = ondasAtivas.get(0);
            
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

        //  APLICA A MOVIMENTAÇÃO E O WALL SMOOTHING
        double anguloDesejado = anguloAbsolutoInimigo + (anguloAtaque * direcaoMovel); 
        
        // Chama a função blindada contra batidas na parede
        anguloDesejado = aplicarWallSmoothing(robot.getX(), robot.getY(), anguloDesejado, direcaoMovel); 
        
        robot.setTurnRightRadians(Utils.normalRelativeAngle(anguloDesejado - robot.getHeadingRadians()));
        
        // Passa a velocidade máxima e garante o movimento
        robot.setMaxVelocity(8.0);
        robot.setAhead(100 * direcaoMovel);

        energiaAnterior = energiaAtual;
    }

    // MÉTODOS  DO SURF


    private void atualizarOndas() {
        for (int i = 0; i < ondasAtivas.size(); i++) {
            OndaInimiga onda = ondasAtivas.get(i);
            double tempoDecorrido = robot.getTime() - onda.tempoCriacao;
            double distanciaPercorrida = tempoDecorrido * onda.velocidadeBala;
            double distanciaParaNos = Math.hypot(onda.origemX - robot.getX(), onda.origemY - robot.getY());            
            
            if (distanciaPercorrida > distanciaParaNos + 50) {
                ondasAtivas.remove(i);
                i--;
            }
        }
    }

    private double preverPerigo(OndaInimiga onda, int direcaoTestada) {
        double anguloTeste = onda.anguloDireto + (Math.PI / 2) * direcaoTestada;
        double xFuturo = robot.getX() + Math.sin(anguloTeste) * 150 * direcaoTestada;
        double yFuturo = robot.getY() + Math.cos(anguloTeste) * 150 * direcaoTestada;
        int indicePerigo = obterIndiceEstatistico(onda, xFuturo, yFuturo);
        return estatisticasPerigo[indicePerigo];
    }

    public void registrarDano(HitByBulletEvent e) {
        robot.setBodyColor(Color.RED); 
        
        if (!ondasAtivas.isEmpty()) {
            OndaInimiga ondaQueAcertou = ondasAtivas.get(0);
            double xDano = e.getBullet().getX();
            double yDano = e.getBullet().getY();
            
            int indicePunido = obterIndiceEstatistico(ondaQueAcertou, xDano, yDano);
            estatisticasPerigo[indicePunido]++;
            
            System.out.println(String.format("[IA SURFER] Risco na zona %d subiu para %d", indicePunido, estatisticasPerigo[indicePunido]));            
            ondasAtivas.remove(0);
        }
    }

    private int obterIndiceEstatistico(OndaInimiga onda, double x, double y) {
        double anguloAteAlvo = Utils.normalAbsoluteAngle(Math.atan2(x - onda.origemX, y - onda.origemY));
        double diferencaAngulo = Utils.normalRelativeAngle(anguloAteAlvo - onda.anguloDireto);
        double anguloMaximoEscape = Math.asin(8.0 / onda.velocidadeBala);
        double guessFactor = diferencaAngulo / anguloMaximoEscape;
        int indice = (int) Math.round((guessFactor * 15) + 15);
        return Math.max(0, Math.min(30, indice));
    }

    //  ALGORITMO DE WALL SMOOTHING 
    private double aplicarWallSmoothing(double xAtual, double yAtual, double anguloDesejado, int orientacaoMovel) {
        // CORREÇÃO: Margem aumentada de 40 para 55! (18 pixels do tanque + espaço de frenagem)
        double margem = 55.0; 
        double stick = 160.0; // Distância do "sensor de ré"
        double campoL = robot.getBattleFieldWidth();
        double campoA = robot.getBattleFieldHeight();


        int loopProtector = 0;

        // Se o ponto futuro bater na parede, vai curvando a trajetória de 0.1 em 0.1 radianos
        while (!trajetoriaSegura(xAtual + Math.sin(anguloDesejado) * stick * orientacaoMovel, 
                                 yAtual + Math.cos(anguloDesejado) * stick * orientacaoMovel, 
                                 campoL, campoA, margem) && loopProtector < 100) {
                                     
            // O segredo matemático: Girar a favor da orientação de movimento faz o robô deslizar na borda
            anguloDesejado += orientacaoMovel * 0.1; 
            loopProtector++;
        }
        return anguloDesejado;
    }

    private boolean trajetoriaSegura(double xTest, double yTest, double L, double A, double margem) {
        return xTest > margem && xTest < L - margem && yTest > margem && yTest < A - margem;
    }

    // ==========================================
    // 📦 ESTRUTURA DE DADOS DA ONDA
    // ==========================================
    class OndaInimiga {
        double origemX, origemY;
        long tempoCriacao;
        double velocidadeBala;
        double potenciaBala;
        double anguloDireto;
    }
}