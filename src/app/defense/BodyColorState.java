package app.defense;

import java.awt.Color;

import robocode.AdvancedRobot;

public class BodyColorState {

    private static final Color ORBIT = new Color(30, 60, 120);
    private static final Color SURFING = Color.CYAN;
    private static final Color HIT = Color.RED;
    private static final Color SHIELD = Color.YELLOW;

    private static final long HIT_FLASH_TICKS = 8;

    private final AdvancedRobot robot;
    private long hitUntilTick = -1;
    private boolean shieldActive = false;

    public BodyColorState(AdvancedRobot robot) {
        this.robot = robot;
    }

    public void registerHit() {
        hitUntilTick = robot.getTime() + HIT_FLASH_TICKS;
    }

    public void setShieldActive(boolean active) {
        shieldActive = active;
    }

    public void update(boolean surfing) {
        long now = robot.getTime();

        if (shieldActive) {
            robot.setBodyColor(SHIELD);
            return;
        }

        if (now <= hitUntilTick) {
            robot.setBodyColor(HIT);
            return;
        }

        if (surfing) {
            robot.setBodyColor(SURFING);
            return;
        }

        robot.setBodyColor(ORBIT);
    }
}
