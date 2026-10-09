package com.paulorchard.islandcraft.waterofarrakis;

/**
 * Turns the engine's per-tick movement flags into one stamina charge per jump and one per vault. One instance per
 * player. No engine types, so it is unit tested.
 *
 * <ul>
 *   <li>A charge is made on the rising edge of a flag (false then true), never per tick while it is held.</li>
 *   <li>A vault (the mantling flag) that starts within {@code window} seconds of a charged jump is the same action:
 *       it is charged only what the jump did not already cover, so a jump into a vault costs the higher of the two
 *       costs, not both. (With both costs equal the vault is free.)</li>
 * </ul>
 */
final class ActionCosts {

    private boolean wasJumping;
    private boolean wasMantling;
    private double secondsSinceJump = Double.POSITIVE_INFINITY;
    private double jumpCharged;

    int jumps;
    int vaults;
    /** What the last jump and the last vault were charged. */
    double lastJumpCharge;
    double lastVaultCharge;
    /** True on the tick a jump or a vault started (the rising edge), whatever it cost. */
    boolean edgeThisTick;
    /** Which of the two started this tick. */
    boolean jumpEdge;
    boolean vaultEdge;

    /**
     * Call once per tick. Returns the stamina to take this tick (0 or more).
     *
     * @param jumpCost  full cost of a jump after the water tier
     * @param vaultCost full cost of a vault after the water tier
     */
    double step(boolean jumping, boolean mantling, double dt, double jumpCost, double vaultCost, double window) {
        secondsSinceJump += dt;
        double charge = 0;
        edgeThisTick = false;
        jumpEdge = false;
        vaultEdge = false;
        if (jumping && !wasJumping) {
            jumps++;
            edgeThisTick = true;
            jumpEdge = true;
            jumpCharged = jumpCost;
            lastJumpCharge = jumpCost;
            secondsSinceJump = 0;
            charge += jumpCost;
        }
        if (mantling && !wasMantling) {
            double cost = vaultCost;
            if (secondsSinceJump <= window) {
                cost = Math.max(0.0, vaultCost - jumpCharged);
            }
            vaults++;
            edgeThisTick = true;
            vaultEdge = true;
            lastVaultCharge = cost;
            charge += cost;
        }
        wasJumping = jumping;
        wasMantling = mantling;
        return charge;
    }

    /** Forget the flags (the player died, went creative, changed world): the next true flag is a new edge. */
    void reset() {
        wasJumping = false;
        wasMantling = false;
        secondsSinceJump = Double.POSITIVE_INFINITY;
        jumpCharged = 0;
    }
}
