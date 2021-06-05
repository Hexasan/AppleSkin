package squeek.appleskin.helpers;

import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import squeek.appleskin.api.food.FoodValues;

public class FoodHelper
{
	public static boolean isFood(ItemStack itemStack)
	{
		return itemStack.getItem().isFood();
	}

	public static FoodValues getDefaultFoodValues(ItemStack itemStack)
	{
		FoodComponent itemFood = itemStack.getItem().getFoodComponent();
		int hunger = itemFood.getHunger();
		float saturationModifier = itemFood.getSaturationModifier();
		return new FoodValues(hunger, saturationModifier);
	}

	public static FoodValues getModifiedFoodValues(ItemStack itemStack, PlayerEntity player)
	{
		if (itemStack.getItem() instanceof DynamicFood)
		{
			DynamicFood food = (DynamicFood) itemStack.getItem();
			int hunger = food.getDynamicHunger(itemStack, player);
			float saturationModifier = food.getDynamicSaturation(itemStack, player);
			return new FoodValues(hunger, saturationModifier);
		}
		return getDefaultFoodValues(itemStack);
	}

	public static boolean isRotten(ItemStack itemStack)
	{
		if (!isFood(itemStack))
			return false;

		for (Pair<StatusEffectInstance, Float> effect : itemStack.getItem().getFoodComponent().getStatusEffects()) {
			if (effect.getFirst() != null && effect.getFirst().getEffectType() != null && effect.getFirst().getEffectType().getType() == StatusEffectType.HARMFUL) {
				return true;
			}
		}
		return false;
	}

	public static float getEstimatedHealthIncrement(ItemStack itemStack, PlayerEntity player)
	{
		if (!isFood(itemStack))
			return 0;

		if (!player.canFoodHeal()) {
			return 0;
		}

		HungerManager stats = player.getHungerManager();
		World world = player.getEntityWorld();

		FoodValues foodValues = getModifiedFoodValues(itemStack, player);

		int foodLevel = Math.min(stats.getFoodLevel() + foodValues.hunger, 20);
		float healthIncrement = 0;

		// health for natural regen
		if (foodLevel >= 18.0F && world != null && world.getGameRules().getBoolean(GameRules.NATURAL_REGENERATION)) {
			float saturationLevel = Math.min(stats.getSaturationLevel() + foodValues.getSaturationIncrement(), (float)foodLevel);
			float exhaustionLevel = HungerHelper.getExhaustion(player);
			healthIncrement = getEstimatedHealthIncrement(foodLevel, saturationLevel, exhaustionLevel);
		}

		// health for regeneration effect
		for (Pair<StatusEffectInstance, Float> effect : itemStack.getItem().getFoodComponent().getStatusEffects()) {
			StatusEffectInstance effectInstance = effect.getFirst();
			if (effectInstance != null && effectInstance.getEffectType() == StatusEffects.REGENERATION) {
				int amplifier = effectInstance.getAmplifier();
				int duration = effectInstance.getDuration();
				if (effectInstance.isPermanent()) {
					duration = Integer.MAX_VALUE;
				}
				healthIncrement += (float)Math.floor(duration / (50 >> amplifier));
				break;
			}
		}

		return healthIncrement;
	}

	public static float getEstimatedHealthIncrement(int foodLevel, float saturationLevel, float exhaustionLevel)
	{

		float health = 0;
		float exhaustionForRegen = 6.0F;
		float exhaustionForConsumed = 4.0F;

		// using timer is still used to avoid some boundary problems
		int foodStarvationTimer = 0;
		while (foodLevel >= 20 && saturationLevel > 0) {
			if (exhaustionLevel > exhaustionForConsumed) {
				exhaustionLevel -= exhaustionForConsumed;
				saturationLevel = Math.max(saturationLevel - 1, 0);
			}
			++foodStarvationTimer;
			if (foodStarvationTimer < 10) {
				continue;
			}
			float limitedSaturationLevel = Math.min(saturationLevel, exhaustionForRegen);
			health += limitedSaturationLevel / exhaustionForRegen;
			exhaustionLevel += limitedSaturationLevel;
			foodStarvationTimer = 0;
		}

		while (foodLevel >= 18) {
			if (exhaustionLevel > exhaustionForConsumed) {
				exhaustionLevel -= exhaustionForConsumed;
				foodLevel -= 1;
			}
			++foodStarvationTimer;
			if (foodStarvationTimer < 80) {
				continue;
			}
			health += 1;
			exhaustionLevel += exhaustionForRegen;
			foodStarvationTimer = 0;
		}

		return health;
	}
}
