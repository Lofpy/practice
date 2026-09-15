package com.poppy.practice.bot;

import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.ComboRules;
import com.poppy.practice.result.MatchParticipantStats;
import net.minecraft.server.v1_8_R3.EnchantmentManager;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemSword;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;
import net.minecraft.server.v1_8_R3.MathHelper;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Random;

final class BotController {
    private static final int SPLASH_HEALING_TWO_DATA = 16421;
    private static final double HEAL_FACING_TOLERANCE_DEGREES = 8.0D;
    private static final double HEAL_POTION_TRAIL_OFFSET = 0.35D;
    private static final double HEAL_POTION_BACKWARD_SPEED = 0.08D;
    private static final double HEAL_POTION_DOWNWARD_SPEED = -0.55D;
    private static final double AIRBORNE_PEARL_SIDE_DISTANCE = 3.0D;
    private static final double NORMAL_PEARL_SIDE_DISTANCE = 1.65D;
    private static final double HEALING_SPLASH_SAFE_DISTANCE = 4.5D;
    private static final int HEALING_SAFE_DISTANCE_MAX_RETREAT_TICKS = 10;
    private static final int AIRBORNE_PEARL_WINDOW_TICKS = 4;
    private static final int BLOCK_HIT_DURATION_TICKS = 2;

    private final BotNpc npc;
    private final EntityPlayer bot;
    private final EntityPlayer targetHandle;
    private final BotSettings settings;
    private final BotCertificationMovement certificationMovement;
    private final boolean consumablesAllowed;
    private final BotComboConsumables comboConsumables;
    private final int pearlCooldownDurationTicks;
    private final MatchParticipantStats botStats;
    private final Random random = new Random();
    private int strafeDirection;
    private int strafeTicks;
    private int clickCooldown;
    private final BotSprintState sprintState = new BotSprintState();
    private boolean movementAdvanced;
    private boolean directCombatFacing;
    private int healCooldown;
    private int healingTicks;
    private int healingPotions;
    private int enderPearlCooldownTicks;
    private int pearlSwitchBackTicks;
    private int pearlSideDirection;
    private int airbornePearlWindowTicks;
    private int blockHitTicks;
    private float healingRetreatYaw;
    private int healingRetreatTicks;
    private int healingRequiredRetreatTicks;
    private int healingMaximumRetreatTicks;
    private boolean healingPotionThrown;
    private boolean emergencyHealing;
    private int aimErrorTicks;
    private double yawError;
    private double pitchError;
    private double lastTargetX;
    private double lastTargetY;
    private double lastTargetZ;
    private boolean hasLastTargetPosition;
    private boolean targetWasOnGround;
    private CombatSnapshot perception;

    BotController(BotNpc npc, Player target, BotSettings settings,
                  MatchParticipantStats botStats) {
        this(npc, target, settings, botStats, true);
    }

    BotController(BotNpc npc, Player target, BotSettings settings,
                  MatchParticipantStats botStats, boolean consumablesAllowed) {
        this(npc, target, settings, botStats, consumablesAllowed ? "nodebuff" : "boxing");
    }

    BotController(BotNpc npc, Player target, BotSettings settings,
                  MatchParticipantStats botStats, String kitId) {
        this.npc = npc;
        this.bot = npc.getHandle();
        this.targetHandle = ((CraftPlayer) target).getHandle();
        this.settings = settings;
        this.certificationMovement = settings.isCertificationMovement()
                ? new BotCertificationMovement(settings) : null;
        this.consumablesAllowed = !BoxingRules.isBoxing(kitId);
        this.comboConsumables = ComboRules.isCombo(kitId) ? new BotComboConsumables(bot) : null;
        this.pearlCooldownDurationTicks = pearlCooldownTicks(kitId, settings);
        this.botStats = botStats;
        this.perception = currentSnapshot();
        // Start the same cooldown a player receives after throwing a pearl.
        // This prevents an immediate pearl on the first fighting tick.
        this.enderPearlCooldownTicks = pearlCooldownDurationTicks;
        this.targetWasOnGround = perception.targetOnGround;
        this.pearlSideDirection = random.nextBoolean() ? 1 : -1;
        this.strafeDirection = random.nextBoolean() ? 1 : -1;
        this.strafeTicks = nextStrafeDuration();
        this.healingPotions = this.consumablesAllowed && comboConsumables == null
                ? settings.getHealingPotionCount() : 0;
    }

    static int pearlCooldownTicks(String kitId, BotSettings settings) {
        return ComboRules.isCombo(kitId) ? ComboRules.ENDER_PEARL_COOLDOWN_SECONDS * 20
                : settings.getEnderPearlCooldownTicks();
    }

    void tick() {
        // A player's client motion is not the server's knockback baseline.
        npc.runClientTick(this::tickClient);
    }

    private void tickClient() {
        movementAdvanced = false;
        directCombatFacing = false;
        npc.applyPendingVelocity();
        npc.ensureSpeedTwo();
        perception = currentSnapshot();
        decrementTimers();
        if (certificationMovement != null) {
            // Expire combo spacing even while healing or switching to a pearl.
            certificationMovement.advanceTick();
        }
        updateAirbornePearlWindow();
        updateStrafe();
        updateAimError();

        if (comboConsumables != null) {
            comboConsumables.replaceWornArmor();
            boolean alreadyEating = comboConsumables.isEating();
            boolean eating = comboConsumables.tick();
            if (eating) {
                suspendCertificationMovement();
                if (!alreadyEating) {
                    blockHitTicks = 0;
                    pearlSwitchBackTicks = 0;
                    healingRetreatYaw = BotMovement.oppositeYaw(BotMovement.yawTo(
                            perception.targetX - bot.locX, perception.targetZ - bot.locZ));
                }
                boolean facingAway = faceHealingRetreatDirection();
                move(facingAway ? 1.0F : 0.0F, 0.0F);
                rememberTargetPosition();
                tickPhysics();
                return;
            }
        }

        if (shouldBeginHealing()) {
            stopBlocking();
            emergencyHealing = shouldUseEmergencyHealing();
            prepareHealingRetreat(emergencyHealing);
            healingTicks = healingSequenceBudget(healingMaximumRetreatTicks);
            healingRetreatYaw = BotMovement.oppositeYaw(BotMovement.yawTo(
                    perception.targetX - bot.locX, perception.targetZ - bot.locZ));
            healingRetreatTicks = 0;
            healingPotionThrown = false;
            bot.inventory.itemInHandIndex = 2;
            bot.inventory.setItem(2, healingPotion());
        }

        if (healingTicks > 0) {
            suspendCertificationMovement();
            tickHealing();
        } else {
            tickCombat();
        }
        rememberTargetPosition();
        tickPhysics();
    }

    private void tickPhysics() {
        if (npc.isValid()) {
            // Fake players are intentionally absent from PlayerList, so WindSpigot
            // does not call EntityPlayer#l() for them. This is the player physics
            // tick that consumes aZ/ba and applies movement, gravity and friction.
            double previousY = bot.locY;
            float horizontalDrag = 0.91F;
            if (bot.onGround) {
                horizontalDrag *= bot.world.getType(MathHelper.floor(bot.locX),
                        MathHelper.floor(bot.locY) - 1, MathHelper.floor(bot.locZ))
                        .getBlock().frictionFactor;
            }
            boolean usingItem = isUsingItem();
            bot.aZ = BotMovement.itemUseInput(bot.aZ, usingItem);
            bot.ba = BotMovement.itemUseInput(bot.ba, usingItem);
            // Run after queued knockback and attacks, immediately before native
            // movement, so continued Combo descent does not need another hit.
            npc.applyVerticalVelocityControl();
            bot.l();
            // Native gravity accelerates motY after movement. Keep the cached
            // client velocity gentle too; the callback releases it on landing.
            npc.applyVerticalVelocityControl();
            // Collisions can also change server motion between bot ticks. It
            // needs normal drag, without stepping the entity a second time.
            npc.advanceServerMotion(horizontalDrag);
            if (npc.isValid()) {
                // EntityPlayer's movement hook does not update fall distance;
                // real PlayerConnection reports accepted movement separately.
                bot.a(bot.locY - previousY, bot.onGround);
                if (directCombatFacing) {
                    // Native movement turns the torso toward strafing. Re-align
                    // at the new position without changing movement or knockback.
                    snapFaceTarget();
                }
            }
        }
    }

    private void tickCombat() {
        boolean targetFacingAway = isTargetFacingAway();
        double distance = horizontalDistance();
        boolean hasLineOfSight = bot.hasLineOfSight(targetHandle);
        // Once close enough to exchange swings, look at the opponent's current
        // eyes. Prediction and random aim error are only used while approaching.
        directCombatFacing = BotAttackPlan.shouldSwing(distance, settings.getSwingRange(),
                perception.targetY - bot.locY, hasLineOfSight, 0.0D);
        if (directCombatFacing) {
            snapFaceTarget();
        } else {
            facePredictedTarget();
        }
        float forward = targetFacingAway ? 1.0F : BotMovement.forwardInput(distance,
                settings.getPreferredDistance(), settings.getRetreatDistance(), bot.noDamageTicks > 0);
        float strafe = BotMovement.strafeInput(settings.isStrafeEnabled(), targetFacingAway,
                settings.getStrafeInput(), strafeDirection);
        if (certificationMovement != null) {
            forward = certificationMovement.forwardInput(distance, relativeHorizontalSpeed(distance),
                    targetFacingAway, bot.noDamageTicks > 0);
            strafe = settings.isStrafeEnabled()
                    ? certificationMovement.strafeInput(settings.getStrafeInput(), strafeDirection,
                            targetFacingAway, forward) : 0.0F;
        }
        float targetYaw = BotMovement.yawTo(perception.targetX - bot.locX,
                perception.targetZ - bot.locZ);
        boolean willSwing = BotAttackPlan.shouldAttemptAttack(clickCooldown, targetHandle.noDamageTicks)
                && BotAttackPlan.shouldSwing(distance, settings.getSwingRange(),
                perception.targetY - bot.locY, hasLineOfSight,
                BotMovement.angleDifference(bot.yaw, targetYaw));
        if (willSwing) {
            // Release right-click before trying to sprint/left-click again.
            stopBlocking();
        }
        move(forward, strafe);

        if (tryThrowEnderPearl() || pearlSwitchBackTicks > 0) {
            // Keep the dedicated throwing direction for the pearl's visual tick.
            directCombatFacing = false;
            suspendCertificationMovement();
            return;
        }

        if (directCombatFacing) {
            // A rejected pearl spawn may have temporarily changed the view too.
            snapFaceTarget();
        }

        if (!willSwing) {
            return;
        }

        stopBlocking();
        bot.bw();
        clickCooldown = nextClickDelay();
        if (!BotAttackPlan.shouldDamage(distance, settings.getAttackRange())) {
            return;
        }
        boolean clientSlowdown = sprintState.isSprinting() || EnchantmentManager.a(bot) > 0;
        long landedHitsBefore = npc.getLandedMeleeHits();
        npc.runServerAttack(() -> bot.attack(targetHandle));
        if (clientSlowdown) {
            // A real client's attack against another player applies this even
            // when the server rejects damage during hurt-time. Do it once,
            // independently of the native server-side attack slowdown.
            npc.applyClientAttackSlowdown();
            applySprintTransition(sprintState.stopAfterAttack());
        }
        beginBlockHit();
        if (npc.getLandedMeleeHits() > landedHitsBefore) {
            sprintState.scheduleReset(settings.getSprintResetTicks());
            if (certificationMovement != null) {
                certificationMovement.recordLandedHit();
                // Include this successful-hit tick in the straight combo window;
                // native physics has not consumed the movement inputs yet.
                bot.aZ = 0.0F;
            }
        }
    }

    private void suspendCertificationMovement() {
        if (certificationMovement != null) {
            certificationMovement.suspend();
        }
    }

    private double relativeHorizontalSpeed(double distance) {
        if (distance < 0.000001D) {
            return 0.0D;
        }
        double targetVelocityX = hasLastTargetPosition ? perception.targetX - lastTargetX : 0.0D;
        double targetVelocityZ = hasLastTargetPosition ? perception.targetZ - lastTargetZ : 0.0D;
        // This is read inside runClientTick: bot.motX/Z is its actual client
        // momentum, not the independent server-side knockback baseline.
        return ((targetVelocityX - bot.motX) * (perception.targetX - bot.locX)
                + (targetVelocityZ - bot.motZ) * (perception.targetZ - bot.locZ)) / distance;
    }

    private void tickHealing() {
        stopBlocking();
        if (!emergencyHealing && shouldUseEmergencyHealing()) {
            emergencyHealing = true;
            prepareHealingRetreat(true);
        }
        boolean facingAway = faceHealingRetreatDirection();
        move(facingAway ? 1.0F : 0.0F, 0.0F);
        if (facingAway && !healingPotionThrown
                && BotHealingPlan.isRetreatComplete(++healingRetreatTicks,
                healingRequiredRetreatTicks, healingMaximumRetreatTicks,
                horizontalDistance(), HEALING_SPLASH_SAFE_DISTANCE)) {
            bot.bw();
            int requestedPotions = BotHealingPlan.potionsForHealth(perception.botHealth,
                    settings.getHealingDoublePotionHealthThreshold(), healingPotions);
            int thrownPotions = throwHealingPotionsIntoRetreatPath(
                    requestedPotions, BotHealingPlan.shouldSplashImmediately(
                            emergencyHealing, bot.onGround));
            if (thrownPotions > 0) {
                healingPotions -= thrownPotions;
                healingPotionThrown = true;
                snapFaceTarget();
                move(0.0F, 0.0F);
                healingTicks = 1;
            }
        }
        healingTicks--;
        if (healingTicks == 0) {
            bot.inventory.itemInHandIndex = 0;
            healCooldown = settings.getHealingCooldownTicks();
        }
    }

    private int healingSequenceBudget(int retreatTicks) {
        int turnTicks = (int) Math.ceil(180.0D / Math.max(1.0D,
                settings.getMaximumYawChange()));
        return turnTicks + retreatTicks + 5;
    }

    private void prepareHealingRetreat(boolean emergency) {
        healingRequiredRetreatTicks = emergency
                ? settings.getHealingEmergencyRetreatBeforeThrowTicks()
                : settings.getHealingRetreatBeforeThrowTicks();
        healingMaximumRetreatTicks = BotHealingPlan.maximumRetreatTicks(
                healingRequiredRetreatTicks, horizontalDistance(),
                HEALING_SPLASH_SAFE_DISTANCE, HEALING_SAFE_DISTANCE_MAX_RETREAT_TICKS);
    }

    private boolean faceHealingRetreatDirection() {
        bot.yaw = BotMovement.rotateToward(bot.yaw, healingRetreatYaw,
                settings.getMaximumYawChange());
        bot.pitch = BotMovement.rotateToward(bot.pitch, 8.0F,
                settings.getMaximumPitchChange());
        copyBodyRotation();
        return BotMovement.angleDifference(bot.yaw, healingRetreatYaw)
                <= HEAL_FACING_TOLERANCE_DEGREES;
    }

    private void facePredictedTarget() {
        double velocityX = hasLastTargetPosition ? perception.targetX - lastTargetX : 0.0D;
        double velocityY = hasLastTargetPosition ? perception.targetY - lastTargetY : 0.0D;
        double velocityZ = hasLastTargetPosition ? perception.targetZ - lastTargetZ : 0.0D;
        double targetX = perception.targetX + velocityX * settings.getPredictionTicks();
        double targetY = perception.targetY + perception.targetHeadHeight
                + velocityY * settings.getPredictionTicks();
        double targetZ = perception.targetZ + velocityZ * settings.getPredictionTicks();
        lookAt(targetX, targetY, targetZ, yawError, pitchError);
    }

    private void snapFaceTarget() {
        double deltaX = perception.targetX - bot.locX;
        double deltaY = perception.targetY + perception.targetHeadHeight
                - (bot.locY + bot.getHeadHeight());
        double deltaZ = perception.targetZ - bot.locZ;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        bot.yaw = BotMovement.yawTo(deltaX, deltaZ);
        bot.pitch = BotMovement.pitchTo(deltaY, horizontal);
        copyBodyRotation();
    }

    private int throwHealingPotionsIntoRetreatPath(int potionCount, boolean splashImmediately) {
        double yawRadians = Math.toRadians(bot.yaw);
        double backwardX = Math.sin(yawRadians);
        double backwardZ = -Math.cos(yawRadians);
        double sideX = -backwardZ;
        double sideZ = backwardX;
        int thrown = 0;
        for (int index = 0; index < potionCount; index++) {
            double sideOffset = potionCount == 1 ? 0.0D : (index == 0 ? -0.06D : 0.06D);
            BotHealingPotion potion = new BotHealingPotion(bot.world, bot, healingPotion());
            potion.setPosition(bot.locX + backwardX * HEAL_POTION_TRAIL_OFFSET
                            + sideX * sideOffset,
                    bot.locY + 0.45D,
                    bot.locZ + backwardZ * HEAL_POTION_TRAIL_OFFSET
                            + sideZ * sideOffset);
            potion.yaw = bot.yaw;
            potion.pitch = 90.0F;
            potion.motX = backwardX * HEAL_POTION_BACKWARD_SPEED;
            potion.motY = HEAL_POTION_DOWNWARD_SPEED;
            potion.motZ = backwardZ * HEAL_POTION_BACKWARD_SPEED;
            if (bot.world.addEntity(potion)) {
                thrown++;
                if (splashImmediately) {
                    potion.splashOn(bot);
                }
            }
        }
        return thrown;
    }

    int getRemainingHealingPotions() {
        return healingPotions;
    }

    private void lookAt(double x, double y, double z, double addedYaw, double addedPitch) {
        double deltaX = x - bot.locX;
        double deltaY = y - (bot.locY + bot.getHeadHeight());
        double deltaZ = z - bot.locZ;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float desiredYaw = (float) (BotMovement.yawTo(deltaX, deltaZ) + addedYaw);
        float desiredPitch = (float) (BotMovement.pitchTo(deltaY, horizontal) + addedPitch);
        bot.yaw = BotMovement.rotateToward(bot.yaw, desiredYaw, settings.getMaximumYawChange());
        bot.pitch = BotMovement.rotateToward(bot.pitch, desiredPitch, settings.getMaximumPitchChange());
        copyBodyRotation();
    }

    private void copyBodyRotation() {
        bot.aK = bot.yaw;
        bot.aI = bot.yaw;
        bot.aJ = bot.yaw;
    }

    private void move(float forward, float strafe) {
        if (!movementAdvanced) {
            applySprintTransition(sprintState.advance(forward >= 0.8F
                    && !isUsingItem() && bot.getBukkitEntity().getFoodLevel() > 6));
            movementAdvanced = true;
        }
        // Feed the same forward/strafe fields used by a real player's movement tick.
        // The explicit EntityPlayer#l() call consumes them exactly once.
        bot.aZ = strafe;
        bot.ba = sprintState.isMovementAllowed() ? forward : 0.0F;
    }

    private void applySprintTransition(BotSprintState.Transition transition) {
        if (transition == BotSprintState.Transition.NONE) {
            return;
        }
        boolean sprinting = transition == BotSprintState.Transition.START;
        // Mirror PlayerConnection's START/STOP_SPRINTING handling. Only a real
        // state transition may re-arm the sprint knockback flag.
        bot.setSprinting(sprinting);
        bot.setExtraKnockback(sprinting);
    }

    private boolean shouldBeginHealing() {
        boolean emergency = shouldUseEmergencyHealing();
        return comboConsumables == null && consumablesAllowed
                && settings.isHealingEnabled() && healingPotions > 0
                && (healCooldown == 0 || emergency)
                && healingTicks == 0 && perception.botHealth <= settings.getHealingHealth()
                && (bot.onGround || emergency);
    }

    private boolean shouldUseEmergencyHealing() {
        return BotHealingPlan.isEmergency(perception.botHealth,
                settings.getHealingDoublePotionHealthThreshold(),
                perception.unansweredMeleeHits,
                settings.getHealingEmergencyUnansweredHits());
    }

    private void updateStrafe() {
        if (!settings.isStrafeEnabled()) {
            return;
        }
        if (--strafeTicks > 0) {
            return;
        }
        if (random.nextDouble() < 0.72D) {
            strafeDirection = -strafeDirection;
        }
        strafeTicks = nextStrafeDuration();
    }

    private void updateAimError() {
        if (--aimErrorTicks > 0) {
            return;
        }
        yawError = gaussianError(settings.getAimErrorDegrees());
        pitchError = gaussianError(settings.getAimErrorDegrees() * 0.55D);
        aimErrorTicks = 5 + random.nextInt(8);
    }

    private double gaussianError(double maximum) {
        if (maximum <= 0.0D) {
            return 0.0D;
        }
        return Math.max(-maximum, Math.min(maximum, random.nextGaussian() * maximum * 0.45D));
    }

    private void decrementTimers() {
        if (clickCooldown > 0) clickCooldown--;
        if (healCooldown > 0) healCooldown--;
        if (enderPearlCooldownTicks > 0) enderPearlCooldownTicks--;
        if (blockHitTicks > 0 && --blockHitTicks == 0) {
            stopBlocking();
        }
        if (pearlSwitchBackTicks > 0 && --pearlSwitchBackTicks == 0 && healingTicks == 0) {
            bot.inventory.itemInHandIndex = 0;
        }
    }

    private void updateAirbornePearlWindow() {
        if (BotPearlTargeting.becameAirborne(targetWasOnGround,
                perception.targetOnGround)) {
            airbornePearlWindowTicks = AIRBORNE_PEARL_WINDOW_TICKS;
        } else if (perception.targetOnGround) {
            airbornePearlWindowTicks = 0;
        } else if (airbornePearlWindowTicks > 0) {
            airbornePearlWindowTicks--;
        }
        targetWasOnGround = perception.targetOnGround;
    }

    private boolean isTargetFacingAway() {
        return BotPearlTargeting.isFacingAway(
                perception.targetYaw, perception.targetX, perception.targetZ,
                bot.locX, bot.locZ);
    }

    private boolean tryThrowEnderPearl() {
        if (!consumablesAllowed || !bot.onGround || enderPearlCooldownTicks > 0 || pearlSwitchBackTicks > 0) {
            return false;
        }
        ItemStack pearls = bot.inventory.getItem(1);
        if (pearls == null || pearls.count <= 0 || pearls.getItem() != Items.ENDER_PEARL) {
            return false;
        }

        BotPearlTargeting.Target destination;
        boolean airborneTarget = airbornePearlWindowTicks > 0;
        if (airborneTarget) {
            destination = BotPearlTargeting.beside(perception.targetX,
                    perception.targetZ, perception.targetYaw, pearlSideDirection,
                    AIRBORNE_PEARL_SIDE_DISTANCE);
        } else {
            destination = BotPearlTargeting.beside(perception.targetX,
                    perception.targetZ, perception.targetYaw, pearlSideDirection,
                    NORMAL_PEARL_SIDE_DISTANCE);
        }
        if (!launchEnderPearl(destination.getX(), perception.targetY + 0.15D,
                destination.getZ())) {
            bot.inventory.itemInHandIndex = 0;
            return false;
        }

        pearls.count--;
        if (pearls.count <= 0) {
            bot.inventory.setItem(1, null);
        }
        enderPearlCooldownTicks = pearlCooldownDurationTicks;
        pearlSwitchBackTicks = 1;
        if (airborneTarget) {
            airbornePearlWindowTicks = 0;
        }
        pearlSideDirection = -pearlSideDirection;
        return true;
    }

    private boolean launchEnderPearl(double targetX, double targetY, double targetZ) {
        double startX = bot.locX;
        double startY = bot.locY + bot.getHeadHeight() - 0.1D;
        double startZ = bot.locZ;
        double deltaX = targetX - startX;
        double deltaZ = targetZ - startZ;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double deltaY = targetY - startY + horizontal * 0.16D;

        stopBlocking();
        bot.inventory.itemInHandIndex = 1;
        bot.yaw = BotMovement.yawTo(deltaX, deltaZ);
        bot.pitch = BotMovement.pitchTo(deltaY, horizontal);
        copyBodyRotation();
        bot.bw();

        BotEnderPearl pearl = new BotEnderPearl(bot.world, bot,
                targetX, targetY, targetZ);
        pearl.setPosition(startX, startY, startZ);
        pearl.shoot(deltaX, deltaY, deltaZ, 1.5F, 0.0F);
        return bot.world.addEntity(pearl);
    }

    private void beginBlockHit() {
        if (botStats == null
                || botStats.getGuards() >= MatchParticipantStats.MAX_GUARDS_PER_MATCH) {
            return;
        }
        ItemStack sword = bot.inventory.getItemInHand();
        if (sword == null || !(sword.getItem() instanceof ItemSword)) {
            return;
        }
        bot.a(sword, sword.getItem().d(sword));
        blockHitTicks = BLOCK_HIT_DURATION_TICKS;
    }

    private void stopBlocking() {
        blockHitTicks = 0;
        if (bot.isBlocking()) {
            bot.bV();
        }
    }

    private boolean isUsingItem() {
        return bot.isBlocking() || (comboConsumables != null && bot.bS());
    }

    private int nextStrafeDuration() {
        int difference = settings.getStrafeSwitchMaximumTicks() - settings.getStrafeSwitchMinimumTicks();
        return settings.getStrafeSwitchMinimumTicks() + (difference == 0 ? 0 : random.nextInt(difference + 1));
    }

    private int nextClickDelay() {
        double cps = settings.getMinimumCps()
                + random.nextDouble() * (settings.getMaximumCps() - settings.getMinimumCps());
        return Math.max(1, (int) Math.round(20.0D / cps));
    }

    private double horizontalDistance() {
        double deltaX = perception.targetX - bot.locX;
        double deltaZ = perception.targetZ - bot.locZ;
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private void rememberTargetPosition() {
        lastTargetX = perception.targetX;
        lastTargetY = perception.targetY;
        lastTargetZ = perception.targetZ;
        hasLastTargetPosition = true;
    }

    private CombatSnapshot currentSnapshot() {
        return new CombatSnapshot(targetHandle.locX, targetHandle.locY, targetHandle.locZ,
                targetHandle.getHeadHeight(), targetHandle.yaw, targetHandle.onGround,
                bot.getHealth(),
                npc.getUnansweredMeleeHits());
    }

    private ItemStack healingPotion() {
        return new ItemStack(Items.POTION, 1, SPLASH_HEALING_TWO_DATA);
    }

    private static final class CombatSnapshot {
        private final double targetX;
        private final double targetY;
        private final double targetZ;
        private final float targetHeadHeight;
        private final float targetYaw;
        private final boolean targetOnGround;
        private final float botHealth;
        private final int unansweredMeleeHits;

        private CombatSnapshot(double targetX, double targetY, double targetZ,
                               float targetHeadHeight, float targetYaw,
                               boolean targetOnGround, float botHealth,
                               int unansweredMeleeHits) {
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.targetHeadHeight = targetHeadHeight;
            this.targetYaw = targetYaw;
            this.targetOnGround = targetOnGround;
            this.botHealth = botHealth;
            this.unansweredMeleeHits = unansweredMeleeHits;
        }
    }
}
