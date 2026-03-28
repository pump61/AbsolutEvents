package com.absolutgg.absolutevents.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Cuboid {

    private static final Random RANDOM = new Random();

    private final int xMin;
    private final int xMax;
    private final int yMin;
    private final int yMax;
    private final int zMin;
    private final int zMax;

    private final double xMinCentered;
    private final double xMaxCentered;
    private final double yMinCentered;
    private final double yMaxCentered;
    private final double zMinCentered;
    private final double zMaxCentered;

    private final World world;

    public Cuboid(Location point1, Location point2) {
        this.xMin = Math.min(point1.getBlockX(), point2.getBlockX());
        this.xMax = Math.max(point1.getBlockX(), point2.getBlockX());

        this.yMin = Math.min(point1.getBlockY(), point2.getBlockY());
        this.yMax = Math.max(point1.getBlockY(), point2.getBlockY());

        this.zMin = Math.min(point1.getBlockZ(), point2.getBlockZ());
        this.zMax = Math.max(point1.getBlockZ(), point2.getBlockZ());

        this.world = point1.getWorld();

        this.xMinCentered = this.xMin + 0.5;
        this.xMaxCentered = this.xMax + 0.5;

        this.yMinCentered = this.yMin + 0.5;
        this.yMaxCentered = this.yMax + 0.5;

        this.zMinCentered = this.zMin + 0.5;
        this.zMaxCentered = this.zMax + 0.5;
    }

    public List<Block> getBlocks() {
        List<Block> blocks = new ArrayList<>(getTotalBlockSize());

        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    blocks.add(world.getBlockAt(x, y, z));
                }
            }
        }

        return blocks;
    }

    public Location getCenter() {
        return new Location(
                world,
                (xMax - xMin) / 2.0 + xMin,
                (yMax - yMin) / 2.0 + yMin,
                (zMax - zMin) / 2.0 + zMin
        );
    }

    public Location getRandomLocation() {
        int x = RANDOM.nextInt(Math.abs(xMax - xMin) + 1) + xMin;
        int y = RANDOM.nextInt(Math.abs(yMax - yMin) + 1) + yMin;
        int z = RANDOM.nextInt(Math.abs(zMax - zMin) + 1) + zMin;

        return new Location(world, x, y, z);
    }

    public Location getPoint1() {
        return new Location(world, xMin, yMin, zMin);
    }

    public Location getPoint2() {
        return new Location(world, xMax, yMax, zMax);
    }

    public int getHeight() {
        return yMax - yMin + 1;
    }

    public int getXWidth() {
        return xMax - xMin + 1;
    }

    public int getZWidth() {
        return zMax - zMin + 1;
    }

    public int getTotalBlockSize() {
        return getHeight() * getXWidth() * getZWidth();
    }

    public double getDistance() {
        return getPoint1().distance(getPoint2());
    }

    public double getDistanceSquared() {
        return getPoint1().distanceSquared(getPoint2());
    }

    public boolean isIn(Location location) {
        return location.getWorld() == world
                && location.getBlockX() >= xMin
                && location.getBlockX() <= xMax
                && location.getBlockY() >= yMin
                && location.getBlockY() <= yMax
                && location.getBlockZ() >= zMin
                && location.getBlockZ() <= zMax;
    }

    public boolean isIn(Player player) {
        return isIn(player.getLocation());
    }

    public boolean isInWithMargin(Location loc, double margin) {
        return loc.getWorld() == world
                && loc.getX() >= xMinCentered - margin
                && loc.getX() <= xMaxCentered + margin
                && loc.getY() >= yMinCentered - margin
                && loc.getY() <= yMaxCentered + margin
                && loc.getZ() >= zMinCentered - margin
                && loc.getZ() <= zMaxCentered + margin;
    }

    public boolean isInWithMargin(Player player, double margin) {
        return isInWithMargin(player.getLocation(), margin);
    }

    public boolean isInWithMarginY(Location loc, double margin) {
        return loc.getWorld() == world
                && loc.getBlockX() >= xMin
                && loc.getBlockX() <= xMax
                && loc.getBlockY() >= yMin
                && loc.getBlockY() <= yMax + margin
                && loc.getBlockZ() >= zMin
                && loc.getBlockZ() <= zMax;
    }

    public boolean isInWithMarginY(Player player, double margin) {
        return isInWithMarginY(player.getLocation(), margin);
    }

    public boolean isInWithMargeY(Location loc, double margin) {
        return isInWithMarginY(loc, margin);
    }

    public boolean isInWithMargeY(Player player, double margin) {
        return isInWithMarginY(player, margin);
    }

    public Block[] corners() {
        Block[] blocks = new Block[8];

        blocks[0] = world.getBlockAt(xMin, yMin, zMin);
        blocks[1] = world.getBlockAt(xMin, yMin, zMax);
        blocks[2] = world.getBlockAt(xMin, yMax, zMin);
        blocks[3] = world.getBlockAt(xMin, yMax, zMax);

        blocks[4] = world.getBlockAt(xMax, yMin, zMin);
        blocks[5] = world.getBlockAt(xMax, yMin, zMax);
        blocks[6] = world.getBlockAt(xMax, yMax, zMin);
        blocks[7] = world.getBlockAt(xMax, yMax, zMax);

        return blocks;
    }
}