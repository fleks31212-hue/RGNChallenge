package com.rngchallenge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.*;

public class RNGChallenge implements ModInitializer {
    private static final int ROUND_TICKS = 20 * 60;
    private static final Random RANDOM = new Random();
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    private record Task(String description, Item item, int amount, int reward) {}
    private static final List<Task> TASKS = List.of(
        new Task("Добудь 1 красную шерсть", Items.RED_WOOL, 1, 2),
        new Task("Добудь 16 булыжников", Items.COBBLESTONE, 16, 1),
        new Task("Добудь 5 яблок", Items.APPLE, 5, 3),
        new Task("Добудь 10 угля", Items.COAL, 10, 2),
        new Task("Добудь 16 дубовых досок", Items.OAK_PLANKS, 16, 1),
        new Task("Добудь 5 железных слитков", Items.IRON_INGOT, 5, 4),
        new Task("Добудь 3 хлеба", Items.BREAD, 3, 3),
        new Task("Добудь 8 костей", Items.BONE, 8, 3),
        new Task("Добудь 4 золотых слитка", Items.GOLD_INGOT, 4, 5),
        new Task("Добудь 16 песка", Items.SAND, 16, 2),
        new Task("Добудь 3 кожи", Items.LEATHER, 3, 3),
        new Task("Добудь 5 красных грибов", Items.RED_MUSHROOM, 5, 3),
        new Task("Добудь 8 факелов", Items.TORCH, 8, 2),
        new Task("Добудь 1 жемчуг Края", Items.ENDER_PEARL, 1, 7),
        new Task("Добудь 1 паучий глаз", Items.SPIDER_EYE, 1, 3),
        new Task("Добудь 3 лазурита", Items.LAPIS_LAZULI, 3, 4),
        new Task("Добудь 1 ведро молока", Items.MILK_BUCKET, 1, 4),
        new Task("Добудь 5 гнилой плоти", Items.ROTTEN_FLESH, 5, 2),
        new Task("Добудь 1 алмаз", Items.DIAMOND, 1, 6),
        new Task("Добудь 1 обсидиан", Items.OBSIDIAN, 1, 5)
    );

    private static class PlayerState {
        int ticks;
        int punishmentTicks;
        Task active;
        int startingCount;
        boolean started;
    }

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            STATES.put(handler.player.getUuid(), new PlayerState()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            STATES.remove(handler.player.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerState state = STATES.computeIfAbsent(player.getUuid(), id -> new PlayerState());

            if (state.punishmentTicks > 0) {
                state.punishmentTicks--;
                if (state.punishmentTicks % 300 == 0) {
                    ServerWorld world = player.getServerWorld();
                    BlockPos p = player.getBlockPos();
                    world.createExplosion(null, p.getX() + 0.5, p.getY(), p.getZ() + 0.5,
                        1.5f, World.ExplosionSourceType.TNT);
                    player.sendMessage(Text.literal("§cRNG: взрыв рядом с тобой!"), true);
                }
            }

            if (!state.started) {
                state.started = true;
                state.ticks = 0;
                continue;
            }

            state.ticks++;
            if (state.active != null && count(player, state.active.item()) >= state.startingCount + state.active.amount()) {
                complete(player, state);
            } else if (state.ticks >= ROUND_TICKS) {
                if (state.active != null) punish(player, state);
                startTask(player, state);
            }
        }
    }

    private static int count(ServerPlayerEntity player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) total += stack.getCount();
        }
        return total;
    }

    private static void startTask(ServerPlayerEntity player, PlayerState state) {
        state.active = TASKS.get(RANDOM.nextInt(TASKS.size()));
        state.startingCount = count(player, state.active.item());
        state.ticks = 0;
        player.sendMessage(Text.literal("§6§l[RNG CHALLENGE] §eНовое задание!"), false);
        player.sendMessage(Text.literal("§f" + state.active.description() +
            " §7(60 секунд)"), false);
        player.sendMessage(Text.literal("§7Награда: §b" + state.active.reward() + " алмаз(ов)"), false);
    }

    private static void complete(ServerPlayerEntity player, PlayerState state) {
        int reward = state.active.reward();
        player.giveItemStack(new ItemStack(Items.DIAMOND, reward));
        player.sendMessage(Text.literal("§a§lЗАДАНИЕ ВЫПОЛНЕНО! §b+" + reward + " алмаз(ов)"), false);
        state.active = null;
        state.ticks = 0;
    }

    private static void punish(ServerPlayerEntity player, PlayerState state) {
        ServerWorld world = player.getServerWorld();
        int effect = RANDOM.nextInt(20);
        switch (effect) {
            case 0 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 2400, 0));
            case 1 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 1200, 2));
            case 2 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 1200, 0));
            case 3 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 1800, 1));
            case 4 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 1800, 1));
            case 5 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 1200, 2));
            case 6 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 200, 0));
            case 7 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 160, 0));
            case 8 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 100, 0));
            case 9 -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 1200, 0));
            case 10 -> player.setOnFireFor(12);
            case 11 -> player.damage(player.getDamageSources().magic(), 4.0f);
            case 12 -> spawnMob(world, EntityType.ZOMBIE, player);
            case 13 -> spawnMob(world, EntityType.SKELETON, player);
            case 14 -> spawnMob(world, EntityType.SPIDER, player);
            case 15 -> spawnMob(world, EntityType.CREEPER, player);
            case 16 -> {
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                if (bolt != null) { bolt.refreshPositionAfterTeleport(player.getX(), player.getY(), player.getZ()); world.spawnEntity(bolt); }
            }
            case 17 -> {
                player.getHungerManager().addExhaustion(40.0f);
                player.sendMessage(Text.literal("§cТы резко проголодался!"), true);
            }
            case 18 -> {
                world.createExplosion(null, player.getX(), player.getY(), player.getZ(), 1.5f, World.ExplosionSourceType.TNT);
            }
            case 19 -> {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 1200, 1));
                state.punishmentTicks = 600;
                player.sendMessage(Text.literal("§cПод тобой будут появляться взрывы 30 секунд!"), false);
            }
        }
        player.sendMessage(Text.literal("§c§l[RNG] Задание провалено! Наказание №" + (effect + 1)), false);
        state.active = null;
        state.ticks = 0;
    }

    private static void spawnMob(ServerWorld world, EntityType<?> type, ServerPlayerEntity player) {
        Entity entity = type.create(world);
        if (entity != null) {
            entity.refreshPositionAfterTeleport(player.getX() + 2.0, player.getY(), player.getZ() + 2.0);
            world.spawnEntity(entity);
        }
    }
}
