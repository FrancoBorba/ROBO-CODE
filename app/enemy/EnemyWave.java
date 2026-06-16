package app.enemy;

/**
 * Representa uma onda/bala disparada pelo inimigo.
 *
 * No Robocode, a bala inimiga não é visível diretamente. Por isso, estimamos
 * sua existência observando a queda de energia do adversário e guardamos os
 * dados necessários para movimento evasivo e bullet shield.
 */
public class EnemyWave {
    public final double originX;
    public final double originY;
    public final long fireTime;
    public final double bulletPower;
    public final double bulletSpeed;
    public final double directAngle;

    public EnemyWave(double originX, double originY, long fireTime,
                     double bulletPower, double bulletSpeed, double directAngle) {
        this.originX = originX;
        this.originY = originY;
        this.fireTime = fireTime;
        this.bulletPower = bulletPower;
        this.bulletSpeed = bulletSpeed;
        this.directAngle = directAngle;
    }

    public double getDistanceTraveled(long currentTime) {
        return Math.max(0, currentTime - fireTime) * bulletSpeed;
    }

    public double getDistanceTo(double x, double y) {
        return Math.hypot(originX - x, originY - y);
    }

    public double getRemainingDistanceTo(double x, double y, long currentTime) {
        return getDistanceTo(x, y) - getDistanceTraveled(currentTime);
    }
}
