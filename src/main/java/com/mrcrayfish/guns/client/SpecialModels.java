package com.mrcrayfish.guns.client;

import com.mrcrayfish.guns.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Author: MrCrayfish
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public enum SpecialModels
{
    FLAME("flame"),
    MINI_GUN_BASE("mini_gun_base"),
    MINI_GUN_BARRELS("mini_gun_barrels"),
    GRENADE_LAUNCHER_BASE("grenade_launcher_base"),
    GRENADE_LAUNCHER_CYLINDER("grenade_launcher_cylinder"),
    FLINT_LOCK("gun/flint_lock"),
    FOUR_ROUND_PISTOL("gun/four_round_pistol"),
    BIG_BOLT_ACTION_RIFILE("gun/big_bolt_action_rifile"),
    BOLT_ACTION_RIFILE("gun/bolt_action_rifile"),
    COWBOY_GUN("gun/cowboy_gun"),
    DOUBLE_BARREL_SHOTGUN("gun/double_barrel_shotgun"),
    DRAGON_BARREL_SHOTGUN("gun/dragon_barrel_shotgun"),
    FLAMETHROWER("gun/flamethrower"),
    HEALING_NEEDLES("gun/healing_needles"),
    HEALTHOOKA("gun/healthooka"),
    MAG_GUN("gun/mag_gun"),
    SLEEPER_ZAP_GUN("gun/sleeper_zap_gun"),
    THE_HAND_CANNON("gun/the_hand_cannon");

    /**
     * The location of an item model in the [MOD_ID]/models/special/[NAME] folder
     */
    private ResourceLocation modelLocation;

    /**
     * Determines if the model should be loaded as a special model.
     */
    private boolean specialModel;

    /**
     * Cached model
     */
    @OnlyIn(Dist.CLIENT)
    private IBakedModel cachedModel;

    /**
     * Sets the model's location
     *
     * @param modelName name of the model file
     */
    SpecialModels(String modelName)
    {
        this(new ResourceLocation(Reference.MOD_ID, "special/" + modelName), true);
    }

    /**
     * Sets the model's location
     *
     * @param resource name of the model file
     * @param specialModel if the model is a special model
     */
    SpecialModels(ResourceLocation resource, boolean specialModel)
    {
        this.modelLocation = resource;
        this.specialModel = specialModel;
    }

    /**
     * Gets the model
     *
     * @return isolated model
     */
    @OnlyIn(Dist.CLIENT)
    public IBakedModel getModel()
    {
        if(this.cachedModel == null)
        {
            IBakedModel model = Minecraft.getInstance().getModelManager().getModel(this.modelLocation);
            if(model == Minecraft.getInstance().getModelManager().getMissingModel())
            {
                return model;
            }
            this.cachedModel = model;
        }
        return this.cachedModel;
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void register(ModelRegistryEvent event)
    {
        for(SpecialModels model : values())
        {
            if(model.specialModel)
            {
                ModelLoader.addSpecialModel(model.modelLocation);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void clearCache()
    {
        for(SpecialModels model : values())
        {
            if(model.specialModel)
            {
                model.cachedModel = null;
            }
        }
    }
}
