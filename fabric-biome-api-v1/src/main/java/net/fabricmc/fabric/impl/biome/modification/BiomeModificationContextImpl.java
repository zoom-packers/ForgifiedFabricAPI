/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.biome.modification;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.BiomeSpecialEffectsBuilder;
import net.neoforged.neoforge.common.world.ClimateSettingsBuilder;
import net.neoforged.neoforge.common.world.EnvironmentAttributeMapBuilder;
import net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import net.minecraft.world.attribute.modifier.MobSpawnSettingsModifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;

/**
 * NeoForge port: all modifications are applied to NeoForge's {@link ModifiableBiomeInfo.BiomeInfo.Builder}
 * rather than to the biome itself. Mob spawns are held in a separate {@link MobSpawnSettingsBuilder} by NeoForge,
 * which overlays them onto {@link EnvironmentAttributes#NATURAL_MOB_SPAWNS} when the biome info is built, so
 * direct changes to that attribute are synced back into the spawn settings builder.
 */
public class BiomeModificationContextImpl implements BiomeModificationContext {
	private final RegistryAccess registries;
	private final Biome biome;
	private final ModifiableBiomeInfo.BiomeInfo.Builder builder;
	private final WeatherContext weather;
	private final AttributesContextImpl attributes;
	private final EffectsContextImpl effects;
	private final GenerationSettingsContextImpl generationSettings;
	private final SpawnSettingsContextImpl spawnSettings;

	public BiomeModificationContextImpl(RegistryAccess registries, Biome biome, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
		this.registries = registries;
		this.biome = biome;
		this.builder = builder;
		this.weather = new WeatherContextImpl();
		this.attributes = new AttributesContextImpl();
		this.effects = new EffectsContextImpl();
		this.generationSettings = new GenerationSettingsContextImpl();
		this.spawnSettings = new SpawnSettingsContextImpl();
	}

	@Override
	public WeatherContext getWeather() {
		return weather;
	}

	@Override
	public AttributesContext getAttributes() {
		return attributes;
	}

	@Override
	public EffectsContext getEffects() {
		return effects;
	}

	@Override
	public GenerationSettingsContext getGenerationSettings() {
		return generationSettings;
	}

	@Override
	public MobSpawnSettingsContext getMobSpawnSettings() {
		return spawnSettings;
	}

	private class WeatherContextImpl implements WeatherContext {
		private final ClimateSettingsBuilder climateSettings = builder.getClimateSettings();

		@Override
		public void setPrecipitation(boolean hasPrecipitation) {
			climateSettings.setHasPrecipitation(hasPrecipitation);
		}

		@Override
		public void setTemperature(float temperature) {
			climateSettings.setTemperature(temperature);
		}

		@Override
		public void setTemperatureModifier(Biome.TemperatureModifier temperatureModifier) {
			climateSettings.setTemperatureModifier(Objects.requireNonNull(temperatureModifier));
		}

		@Override
		public void setDownfall(float downfall) {
			climateSettings.setDownfall(downfall);
		}

		@Override
		public boolean hasPrecipitation() {
			return climateSettings.hasPrecipitation();
		}

		@Override
		public float getTemperature() {
			return climateSettings.getTemperature();
		}

		@Override
		public Biome.TemperatureModifier getTemperatureModifier() {
			return climateSettings.getTemperatureModifier();
		}

		@Override
		public float getDownfall() {
			return climateSettings.getDownfall();
		}
	}

	private class AttributesContextImpl implements AttributesContext {
		private final EnvironmentAttributeMapBuilder attributeMap = builder.getAttributes();

		@Override
		public void addAll(EnvironmentAttributeMap map) {
			attributeMap.putAll(map);

			if (map.contains(EnvironmentAttributes.NATURAL_MOB_SPAWNS)) {
				spawnSettings.syncFromAttributes();
			}
		}

		@Override
		public <T> void set(EnvironmentAttribute<T> key, T value) {
			attributeMap.set(key, value);

			if (key.equals(EnvironmentAttributes.NATURAL_MOB_SPAWNS)) {
				spawnSettings.syncFromAttributes();
			}
		}

		@Override
		public <T, M> void setModifier(EnvironmentAttribute<T> key, AttributeModifier<T, M> modifier, M value) {
			attributeMap.modify(key, modifier, value);

			if (key.equals(EnvironmentAttributes.NATURAL_MOB_SPAWNS)) {
				spawnSettings.syncFromAttributes();
			}
		}

		@Override
		public <T> EnvironmentAttributeMap.@Nullable Entry<T, ?> get(EnvironmentAttribute<T> attribute) {
			if (attribute.equals(EnvironmentAttributes.NATURAL_MOB_SPAWNS)) {
				spawnSettings.flushToAttributes();
			}

			return attributeMap.get(attribute);
		}

		@Override
		public <T> T applyModifier(EnvironmentAttribute<T> attribute, T value) {
			EnvironmentAttributeMap.Entry<T, ?> entry = get(attribute);
			return entry != null ? entry.applyModifier(value) : value;
		}
	}

	private class EffectsContextImpl implements EffectsContext {
		private final BiomeSpecialEffectsBuilder effects = builder.getSpecialEffects();

		@Override
		public void setFogColor(int color) {
			attributes.set(EnvironmentAttributes.FOG_COLOR, ARGB.vector3fFromRGB24(color));
		}

		@Override
		public void setWaterColor(int color) {
			effects.waterColor(color);
		}

		@Override
		public void setWaterFogColor(int color) {
			attributes.set(EnvironmentAttributes.WATER_FOG_COLOR, ARGB.vector3fFromRGB24(color));
		}

		@Override
		public void setSkyColor(int color) {
			attributes.set(EnvironmentAttributes.SKY_COLOR, ARGB.vector3fFromRGB24(color));
		}

		@Override
		public void setFoliageColorOverride(Optional<Integer> color) {
			effects.foliageColorOverride(Objects.requireNonNull(color));
		}

		@Override
		public void setDryFoliageColorOverride(Optional<Integer> color) {
			effects.dryFoliageColorOverride(Objects.requireNonNull(color));
		}

		@Override
		public void setGrassColorOverride(Optional<Integer> color) {
			effects.grassColorOverride(Objects.requireNonNull(color));
		}

		@Override
		public void setGrassColorModifier(BiomeSpecialEffects.GrassColorModifier colorModifier) {
			effects.grassColorModifier(Objects.requireNonNull(colorModifier));
		}

		@Override
		public void setMusicVolume(float volume) {
			attributes.set(EnvironmentAttributes.MUSIC_VOLUME, volume);
		}

		@Override
		public int getWaterColor() {
			return effects.waterColor();
		}

		@Override
		public Optional<Integer> getFoliageColorOverride() {
			return effects.getFoliageColorOverride();
		}

		@Override
		public Optional<Integer> getDryFoliageColorOverride() {
			return effects.getDryFoliageColorOverride();
		}

		@Override
		public Optional<Integer> getGrassColorOverride() {
			return effects.getGrassColorOverride();
		}

		@Override
		public BiomeSpecialEffects.GrassColorModifier getGrassColorModifier() {
			return effects.getGrassColorModifier();
		}
	}

	private class GenerationSettingsContextImpl implements GenerationSettingsContext {
		private final Registry<WorldCarver> carvers = registries.lookupOrThrow(Registries.CARVER);
		private final Registry<PlacedFeature> features = registries.lookupOrThrow(Registries.PLACED_FEATURE);
		private final BiomeGenerationSettingsBuilder generationSettings = builder.getGenerationSettings();

		@Override
		public boolean removeFeature(GenerationStep.Decoration step, ResourceKey<PlacedFeature> placedFeatureKey) {
			PlacedFeature placedFeature = getHolder(features, placedFeatureKey).value();
			List<Holder<PlacedFeature>> featureSteps = generationSettings.getFeatures(step);
			return featureSteps.removeIf(feature -> feature.value() == placedFeature);
		}

		@Override
		public void addFeature(GenerationStep.Decoration step, ResourceKey<PlacedFeature> entry) {
			generationSettings.addFeature(step, getHolder(features, entry));
		}

		@Override
		public void addCarver(ResourceKey<WorldCarver> entry) {
			// We do not need to delay evaluation of this since the registries are already fully built
			generationSettings.addCarver(getHolder(carvers, entry));
		}

		@Override
		public boolean removeCarver(ResourceKey<WorldCarver> carverKey) {
			WorldCarver carver = getHolder(carvers, carverKey).value();
			return generationSettings.getCarvers().removeIf(holder -> holder.value() == carver);
		}

		@Override
		public List<Holder<PlacedFeature>> getFeatures(GenerationStep.Decoration step) {
			return List.copyOf(generationSettings.getFeatures(step));
		}

		@Override
		public boolean hasFeature(GenerationStep.Decoration step, Holder<PlacedFeature> feature) {
			return generationSettings.getFeatures(step).contains(feature);
		}

		@Override
		public List<Holder<WorldCarver>> getCarvers() {
			return List.copyOf(generationSettings.getCarvers());
		}

		@Override
		public boolean hasCarver(Holder<WorldCarver> carver) {
			return generationSettings.getCarvers().contains(carver);
		}
	}

	/**
	 * Gets an entry from the given registry, assuming it's a registry loaded from data packs.
	 * Gives more helpful error messages if an entry is missing by checking if the modder
	 * forgot to data-gen the JSONs corresponding to their built-in objects.
	 */
	private static <T> Holder.Reference<T> getHolder(Registry<T> registry, ResourceKey<T> key) {
		Holder.Reference<T> holder = registry.get(key).orElse(null);

		if (holder == null) {
			// The key doesn't exist in the data packs
			throw new IllegalArgumentException("Couldn't find holder for " + key);
		}

		return holder;
	}

	private class SpawnSettingsContextImpl implements MobSpawnSettingsContext {
		private final MobSpawnSettingsBuilder spawnSettings = builder.getMobSpawnSettings();

		/**
		 * Writes the current spawn settings into the attribute map, mirroring what NeoForge does when building the
		 * biome info, so that reads of {@link EnvironmentAttributes#NATURAL_MOB_SPAWNS} reflect pending spawn changes.
		 */
		private void flushToAttributes() {
			EnvironmentAttributeMapBuilder attributeMap = builder.getAttributes();
			EnvironmentAttributeMap.Entry<MobSpawnSettings, ?> entry = attributeMap.get(EnvironmentAttributes.NATURAL_MOB_SPAWNS);

			if (entry == null || entry.modifier() == MobSpawnSettingsModifier.overlay()) {
				attributeMap.modify(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettingsModifier.overlay(), spawnSettings.build());
			}
		}

		/**
		 * Replaces the contents of the spawn settings builder with the value currently stored in the attribute map,
		 * as NeoForge would otherwise overwrite the attribute with the builder's contents when building the biome info.
		 */
		private void syncFromAttributes() {
			EnvironmentAttributeMap.Entry<MobSpawnSettings, ?> entry = builder.getAttributes().get(EnvironmentAttributes.NATURAL_MOB_SPAWNS);
			MobSpawnSettings settings = entry == null
					? EnvironmentAttributes.NATURAL_MOB_SPAWNS.defaultValue()
					: entry.applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS.defaultValue());

			for (MobCategory category : List.copyOf(spawnSettings.getSpawnerTypes())) {
				spawnSettings.dontOverride(category);
			}

			spawnSettings.removeSpawnCost(spawnSettings.getEntityTypes().toArray(EntityType<?>[]::new));

			for (MobCategory category : settings.definedCategories()) {
				WeightedList<MobSpawnSettings.SpawnerData> spawns = settings.getMobsInCategory(category);

				if (spawns != null) {
					spawnSettings.addAllSpawns(category, spawns);
				}
			}

			if (entry != null && entry.modifier() != MobSpawnSettingsModifier.overlay()) {
				// NeoForge always applies the spawn settings builder as an overlay, so emulate a full replacement
				// by defining every category. Inherited spawn costs cannot be removed this way.
				for (MobCategory category : MobCategory.values()) {
					if (!settings.definedCategories().contains(category)) {
						spawnSettings.noSpawns(category);
					}
				}
			}

			spawnSettings.addAllCosts(settings.allSpawnCosts());
		}

		@Override
		public void setCreatureGenerationProbability(float probability) {
			attributes.set(EnvironmentAttributes.CREATURE_WORLD_GEN_SPAWN_PROBABILITY, probability);
		}

		@Override
		public @UnmodifiableView List<Weighted<MobSpawnSettings.SpawnerData>> getMobs(MobCategory category) {
			Objects.requireNonNull(category);

			// Live view, as the underlying list builder may be replaced when the spawn settings are synced from attributes
			return new AbstractList<>() {
				private List<Weighted<MobSpawnSettings.SpawnerData>> delegate() {
					WeightedList.Builder<MobSpawnSettings.SpawnerData> spawner = spawnSettings.getSpawner(category);
					return spawner == null ? List.of() : spawner.getList();
				}

				@Override
				public Weighted<MobSpawnSettings.SpawnerData> get(int index) {
					return delegate().get(index);
				}

				@Override
				public int size() {
					return delegate().size();
				}
			};
		}

		@Override
		public void addSpawn(MobCategory category, MobSpawnSettings.SpawnerData data, int weight) {
			Objects.requireNonNull(category);
			Objects.requireNonNull(data);

			spawnSettings.addAllSpawns(category, WeightedList.of(List.of(new Weighted<>(data, weight))));
		}

		@Override
		public boolean removeSpawns(BiPredicate<MobCategory, MobSpawnSettings.SpawnerData> predicate) {
			boolean anyRemoved = false;

			for (MobCategory group : spawnSettings.getSpawnerTypes()) {
				WeightedList.Builder<MobSpawnSettings.SpawnerData> spawner = spawnSettings.getSpawner(group);

				if (spawner == null) {
					continue;
				}

				int oldSize = spawner.getList().size();
				spawner.removeIf(entry -> predicate.test(group, entry.value()));

				if (oldSize > spawner.getList().size()) {
					anyRemoved = true;
				}
			}

			return anyRemoved;
		}

		@Override
		public void addMobCharge(EntityType<?> entityType, double charge, double energyBudget) {
			Objects.requireNonNull(entityType);
			spawnSettings.addMobSpawnCost(entityType, charge, energyBudget);
		}

		@Override
		public void clearMobCharge(EntityType<?> entityType) {
			spawnSettings.removeSpawnCost(entityType);
		}

		@Override
		public MobSpawnSettings.@Nullable MobSpawnCost getMobCharge(EntityType<?> entityType) {
			Objects.requireNonNull(entityType);
			return spawnSettings.getCost(entityType);
		}

		@Override
		public @UnmodifiableView Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> getMobCharges() {
			Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> costs = new LinkedHashMap<>();

			for (EntityType<?> entityType : spawnSettings.getEntityTypes()) {
				MobSpawnSettings.MobSpawnCost cost = spawnSettings.getCost(entityType);

				if (cost != null) {
					costs.put(entityType, cost);
				}
			}

			return Collections.unmodifiableMap(costs);
		}

		@Override
		public @UnmodifiableView Set<MobCategory> getMobCategories() {
			Set<MobCategory> categories = EnumSet.noneOf(MobCategory.class);

			for (MobCategory category : MobCategory.values()) {
				if (!getMobs(category).isEmpty()) {
					categories.add(category);
				}
			}

			return Collections.unmodifiableSet(categories);
		}

		@Override
		public @UnmodifiableView Map<MobCategory, List<Weighted<MobSpawnSettings.SpawnerData>>> getMobs() {
			Map<MobCategory, List<Weighted<MobSpawnSettings.SpawnerData>>> mobs = new EnumMap<>(MobCategory.class);

			for (MobCategory category : MobCategory.values()) {
				List<Weighted<MobSpawnSettings.SpawnerData>> spawns = getMobs(category);

				if (!spawns.isEmpty()) {
					mobs.put(category, new ArrayList<>(spawns));
				}
			}

			return Collections.unmodifiableMap(mobs);
		}
	}
}
