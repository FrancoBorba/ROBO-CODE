package app.enemy;

public class EnemyWave {
    public final double originX;
    public final double originY;
    public final long fireTime;
    public final double bulletPower;
    public final double bulletSpeed;
    public final double directAngle;

    public final double fireDistance;
    public final double fireVelocity;

    public EnemyWave(double originX, double originY, long fireTime,
                     double bulletPower, double bulletSpeed, double directAngle,
                     double fireDistance, double fireVelocity) {
        this.originX = originX;
        this.originY = originY;
        this.fireTime = fireTime;
        this.bulletPower = bulletPower;
        this.bulletSpeed = bulletSpeed;
        this.directAngle = directAngle;
        this.fireDistance = fireDistance;
        this.fireVelocity = fireVelocity;
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