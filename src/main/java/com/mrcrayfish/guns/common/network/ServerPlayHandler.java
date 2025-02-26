package com.mrcrayfish.guns.common.network;

import com.mrcrayfish.guns.Config;
import com.mrcrayfish.guns.GunMod;
import com.mrcrayfish.guns.blockentity.WorkbenchBlockEntity;
import com.mrcrayfish.guns.common.Gun;
import com.mrcrayfish.guns.common.ProjectileManager;
import com.mrcrayfish.guns.common.ShootTracker;
import com.mrcrayfish.guns.common.SpreadTracker;
import com.mrcrayfish.guns.common.container.AttachmentContainer;
import com.mrcrayfish.guns.common.container.WorkbenchContainer;
import com.mrcrayfish.guns.crafting.WorkbenchRecipe;
import com.mrcrayfish.guns.crafting.WorkbenchRecipes;
import com.mrcrayfish.guns.entity.ProjectileEntity;
import com.mrcrayfish.guns.event.GunFireEvent;
import com.mrcrayfish.guns.init.ModEnchantments;
import com.mrcrayfish.guns.init.ModSyncedDataKeys;
import com.mrcrayfish.guns.interfaces.IProjectileFactory;
import com.mrcrayfish.guns.item.GunItem;
import com.mrcrayfish.guns.item.IColored;
import com.mrcrayfish.guns.network.PacketHandler;
import com.mrcrayfish.guns.network.message.MessageBulletTrail;
import com.mrcrayfish.guns.network.message.MessageGunSound;
import com.mrcrayfish.guns.network.message.MessageShoot;
import com.mrcrayfish.guns.util.GunEnchantmentHelper;
import com.mrcrayfish.guns.util.GunModifierHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Predicate;

/**
 * Author: MrCrayfish
 */
public class ServerPlayHandler
{
    private static final Predicate<LivingEntity> HOSTILE_ENTITIES = entity -> entity.getSoundSource() == SoundSource.HOSTILE && !(entity instanceof NeutralMob) && !Config.COMMON.aggroMobs.exemptEntities.get().contains(entity.getType().getRegistryName().toString());

    /**
     * Fires the weapon the player is currently holding.
     * This is only intended for use on the logical server.
     *
     * @param player the player for who's weapon to fire
     */
    public static void handleShoot(MessageShoot message, ServerPlayer player)
    {
        if(!player.isSpectator())
        {
            Level world = player.level;
            ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
            if(heldItem.getItem() instanceof GunItem item && (Gun.hasAmmo(heldItem) || player.isCreative()))
            {
                Gun modifiedGun = item.getModifiedGun(heldItem);
                if(modifiedGun != null)
                {
                    if(MinecraftForge.EVENT_BUS.post(new GunFireEvent.Pre(player, heldItem)))
                        return;

                    /* Updates the yaw and pitch with the clients current yaw and pitch */
                    player.setYRot(message.getRotationYaw());
                    player.setXRot(message.getRotationPitch());

                    ShootTracker tracker = ShootTracker.getShootTracker(player);
                    if(tracker.hasCooldown(item) && tracker.getRemaining(item) > Config.SERVER.cooldownThreshold.get())
                    {
                        GunMod.LOGGER.warn(player.getName().getContents() + "(" + player.getUUID() + ") tried to fire before cooldown finished or server is lagging? Remaining milliseconds: " + tracker.getRemaining(item));
                        return;
                    }
                    tracker.putCooldown(heldItem, item, modifiedGun);

                    if(ModSyncedDataKeys.RELOADING.getValue(player))
                    {
                        ModSyncedDataKeys.RELOADING.setValue(player, false);
                    }

                    if(!modifiedGun.getGeneral().isAlwaysSpread() && modifiedGun.getGeneral().getSpread() > 0.0F)
                    {
                        SpreadTracker.get(player).update(player, item);
                    }

                    int count = modifiedGun.getGeneral().getProjectileAmount();
                    Gun.Projectile projectileProps = modifiedGun.getProjectile();
                    ProjectileEntity[] spawnedProjectiles = new ProjectileEntity[count];
                    for(int i = 0; i < count; i++)
                    {
                        IProjectileFactory factory = ProjectileManager.getInstance().getFactory(projectileProps.getItem());
                        ProjectileEntity projectileEntity = factory.create(world, player, heldItem, item, modifiedGun);
                        projectileEntity.setWeapon(heldItem);
                        projectileEntity.setAdditionalDamage(Gun.getAdditionalDamage(heldItem));
                        world.addFreshEntity(projectileEntity);
                        spawnedProjectiles[i] = projectileEntity;
                        projectileEntity.tick();
                    }
                    if(!projectileProps.isVisible())
                    {
                        ParticleOptions data = GunEnchantmentHelper.getParticle(heldItem);
                        MessageBulletTrail messageBulletTrail = new MessageBulletTrail(spawnedProjectiles, projectileProps, player.getId(), data);
                        PacketHandler.getPlayChannel().send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(player.getX(), player.getY(), player.getZ(), Config.COMMON.network.projectileTrackingRange.get(), player.level.dimension())), messageBulletTrail);
                    }

                    MinecraftForge.EVENT_BUS.post(new GunFireEvent.Post(player, heldItem));

                    if(Config.COMMON.aggroMobs.enabled.get())
                    {
                        double radius = GunModifierHelper.getModifiedFireSoundRadius(heldItem, Config.COMMON.aggroMobs.unsilencedRange.get());
                        double x = player.getX();
                        double y = player.getY() + 0.5;
                        double z = player.getZ();
                        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
                        radius *= radius;
                        double dx, dy, dz;
                        for(LivingEntity entity : world.getEntitiesOfClass(LivingEntity.class, box, HOSTILE_ENTITIES))
                        {
                            dx = x - entity.getX();
                            dy = y - entity.getY();
                            dz = z - entity.getZ();
                            if(dx * dx + dy * dy + dz * dz <= radius)
                            {
                                entity.setLastHurtByMob(Config.COMMON.aggroMobs.angerHostileMobs.get() ? player : entity);
                            }
                        }
                    }

                    ResourceLocation fireSound = getFireSound(heldItem, modifiedGun);
                    if(fireSound != null)
                    {
                        double posX = player.getX();
                        double posY = player.getY() + player.getEyeHeight();
                        double posZ = player.getZ();
                        float volume = GunModifierHelper.getFireSoundVolume(heldItem);
                        float pitch = 0.9F + world.random.nextFloat() * 0.2F;
                        double radius = GunModifierHelper.getModifiedFireSoundRadius(heldItem, Config.SERVER.gunShotMaxDistance.get());
                        boolean muzzle = modifiedGun.getDisplay().getFlash() != null;
                        MessageGunSound messageSound = new MessageGunSound(fireSound, SoundSource.PLAYERS, (float) posX, (float) posY, (float) posZ, volume, pitch, player.getId(), muzzle, false);
                        PacketDistributor.TargetPoint targetPoint = new PacketDistributor.TargetPoint(posX, posY, posZ, radius, player.level.dimension());
                        PacketHandler.getPlayChannel().send(PacketDistributor.NEAR.with(() -> targetPoint), messageSound);
                    }

                    if(!player.isCreative())
                    {
                        CompoundTag tag = heldItem.getOrCreateTag();
                        if(!tag.getBoolean("IgnoreAmmo"))
                        {
                            int level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.RECLAIMED.get(), heldItem);
                            if(level == 0 || player.level.random.nextInt(4 - Mth.clamp(level, 1, 2)) != 0)
                            {
                                tag.putInt("AmmoCount", Math.max(0, tag.getInt("AmmoCount") - 1));
                            }
                        }
                    }

                    player.awardStat(Stats.ITEM_USED.get(item));
                }
            }
            else
            {
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, 0.8F);
            }
        }
    }

    private static ResourceLocation getFireSound(ItemStack stack, Gun modifiedGun)
    {
        ResourceLocation fireSound = null;
        if(GunModifierHelper.isSilencedFire(stack))
        {
            fireSound = modifiedGun.getSounds().getSilencedFire();
        }
        else if(stack.isEnchanted())
        {
            fireSound = modifiedGun.getSounds().getEnchantedFire();
        }
        if(fireSound != null)
        {
            return fireSound;
        }
        return modifiedGun.getSounds().getFire();
    }

    /**
     * Crafts the specified item at the workstation the player is currently using.
     * This is only intended for use on the logical server.
     *
     * @param player the player who is crafting
     * @param id     the id of an item which is registered as a valid workstation recipe
     * @param pos    the block position of the workstation the player is using
     */
    public static void handleCraft(ServerPlayer player, ResourceLocation id, BlockPos pos)
    {
        Level world = player.level;

        if(player.containerMenu instanceof WorkbenchContainer)
        {
            WorkbenchContainer workbench = (WorkbenchContainer) player.containerMenu;
            if(workbench.getPos().equals(pos))
            {
                WorkbenchRecipe recipe = WorkbenchRecipes.getRecipeById(world, id);
                if(recipe == null || !recipe.hasMaterials(player))
                    return;

                recipe.consumeMaterials(player);

                WorkbenchBlockEntity workbenchBlockEntity = workbench.getWorkbench();

                /* Gets the color based on the dye */
                ItemStack stack = recipe.getItem();
                ItemStack dyeStack = workbenchBlockEntity.getInventory().get(0);
                if(dyeStack.getItem() instanceof DyeItem)
                {
                    DyeItem dyeItem = (DyeItem) dyeStack.getItem();
                    int color = dyeItem.getDyeColor().getTextColor();

                    if(stack.getItem() instanceof IColored && ((IColored) stack.getItem()).canColor(stack))
                    {
                        IColored colored = (IColored) stack.getItem();
                        colored.setColor(stack, color);
                        workbenchBlockEntity.getInventory().set(0, ItemStack.EMPTY);
                    }
                }

                Containers.dropItemStack(world, pos.getX() + 0.5, pos.getY() + 1.125, pos.getZ() + 0.5, stack);
            }
        }
    }

    /**
     * @param player
     */
    public static void handleUnload(ServerPlayer player)
    {
        ItemStack stack = player.getMainHandItem();
        if(stack.getItem() instanceof GunItem)
        {
            CompoundTag tag = stack.getTag();
            if(tag != null && tag.contains("AmmoCount", Tag.TAG_INT))
            {
                int count = tag.getInt("AmmoCount");
                tag.putInt("AmmoCount", 0);

                GunItem gunItem = (GunItem) stack.getItem();
                Gun gun = gunItem.getModifiedGun(stack);
                ResourceLocation id = gun.getProjectile().getItem();

                Item item = ForgeRegistries.ITEMS.getValue(id);
                if(item == null)
                {
                    return;
                }

                int maxStackSize = item.getMaxStackSize();
                int stacks = count / maxStackSize;
                for(int i = 0; i < stacks; i++)
                {
                    spawnAmmo(player, new ItemStack(item, maxStackSize));
                }

                int remaining = count % maxStackSize;
                if(remaining > 0)
                {
                    spawnAmmo(player, new ItemStack(item, remaining));
                }
            }
        }
    }

    /**
     * @param player
     * @param stack
     */
    private static void spawnAmmo(ServerPlayer player, ItemStack stack)
    {
        player.getInventory().add(stack);
        if(stack.getCount() > 0)
        {
            player.level.addFreshEntity(new ItemEntity(player.level, player.getX(), player.getY(), player.getZ(), stack.copy()));
        }
    }

    /**
     * @param player
     */
    public static void handleAttachments(ServerPlayer player)
    {
        ItemStack heldItem = player.getMainHandItem();
        if(heldItem.getItem() instanceof GunItem)
        {
            NetworkHooks.openGui(player, new SimpleMenuProvider((windowId, playerInventory, player1) -> new AttachmentContainer(windowId, playerInventory, heldItem), new TranslatableComponent("container.cgm.attachments")));
        }
    }
}
