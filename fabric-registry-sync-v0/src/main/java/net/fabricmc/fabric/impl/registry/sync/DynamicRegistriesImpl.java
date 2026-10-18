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

package net.fabricmc.fabric.impl.registry.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.mojang.serialization.Codec;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;
import org.jetbrains.annotations.Unmodifiable;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryValidator;
import net.minecraft.resources.ResourceKey;

import net.fabricmc.fabric.api.event.registry.DynamicRegistries;

public final class DynamicRegistriesImpl {
	// Pending registrations, handed over to NeoForge in NewDatapackRegistryEvent.
	// NeoForge then appends them to RegistryDataLoader.WORLD_REGISTRIES / RELOADABLE_REGISTRIES,
	// which replaces Fabric's own redirect mixins (WorldLoader, RegistryPatchGenerator, etc.).
	private static final List<RegistryDataLoader.RegistryData<?>> PENDING_WORLD_REGISTRIES = new ArrayList<>();
	private static final List<RegistryDataLoader.RegistryData<?>> PENDING_RELOADABLE_REGISTRIES = new ArrayList<>();
	public static final Set<ResourceKey<? extends Registry<?>>> FABRIC_DYNAMIC_REGISTRY_KEYS = new HashSet<>();
	private static final Set<ResourceKey<? extends Registry<?>>> REGISTERED_KEYS = new HashSet<>();
	public static final Map<ResourceKey<? extends Registry<?>>, Codec<?>> NETWORK_CODECS = new HashMap<>();

	static {
		// NeoForge 26.3: DataPackRegistriesHooks must be initialized before RegistryDataLoader. Its static init
		// reads RegistryDataLoader.WORLD_REGISTRIES, which is still null if RegistryDataLoader's own <clinit>
		// is what triggered it (WORLD_REGISTRIES = DataPackRegistriesHooks.captureWorldRegistries(...)).
		DataPackRegistriesHooks.isWorldRegistry(Registries.BIOME);
	}

	private DynamicRegistriesImpl() {
	}

	public static @Unmodifiable List<RegistryDataLoader.RegistryData<?>> getAllDynamicRegistries() {
		List<RegistryDataLoader.RegistryData<?>> all = new ArrayList<>(RegistryDataLoader.WORLD_REGISTRIES);
		all.addAll(RegistryDataLoader.DIMENSION_REGISTRIES);
		all.addAll(RegistryDataLoader.RELOADABLE_REGISTRIES);
		return List.copyOf(all);
	}

	public static @Unmodifiable List<RegistryDataLoader.RegistryData<?>> getWorldRegistries() {
		return List.copyOf(RegistryDataLoader.WORLD_REGISTRIES);
	}

	public static @Unmodifiable List<RegistryDataLoader.RegistryData<?>> getReloadableRegistries() {
		return List.copyOf(RegistryDataLoader.RELOADABLE_REGISTRIES);
	}

	private static void checkNotRegistered(ResourceKey<? extends Registry<?>> key) {
		if (REGISTERED_KEYS.contains(key) || getAllDynamicRegistries().stream().anyMatch(d -> d.key().equals(key))) {
			throw new IllegalArgumentException("Dynamic registry " + key + " has already been registered!");
		}
	}

	public static <T> void register(ResourceKey<? extends Registry<T>> key, Codec<T> serverCodec) {
		Objects.requireNonNull(key, "Registry key cannot be null");
		Objects.requireNonNull(serverCodec, "Server codec cannot be null");
		checkNotRegistered(key);

		var entry = new RegistryDataLoader.RegistryData<>(key, serverCodec, RegistryValidator.none());
		PENDING_WORLD_REGISTRIES.add(entry);
		REGISTERED_KEYS.add(key);
		FABRIC_DYNAMIC_REGISTRY_KEYS.add(key);
	}

	public static <T> void addSyncedRegistry(ResourceKey<? extends Registry<T>> key, Codec<T> networkCodec, DynamicRegistries.SyncOption... options) {
		Objects.requireNonNull(key, "Registry key cannot be null");
		Objects.requireNonNull(networkCodec, "Network codec cannot be null");
		Objects.requireNonNull(options, "Options cannot be null");

		NETWORK_CODECS.put(key, networkCodec);
		FABRIC_DYNAMIC_REGISTRY_KEYS.add(key);
	}

	public static <T> void registerReloadable(ResourceKey<? extends Registry<T>> key, Codec<T> serverCodec) {
		Objects.requireNonNull(key, "Registry key cannot be null");
		Objects.requireNonNull(serverCodec, "Server codec cannot be null");
		checkNotRegistered(key);

		var entry = new RegistryDataLoader.RegistryData<>(key, serverCodec, RegistryValidator.none());
		PENDING_RELOADABLE_REGISTRIES.add(entry);
		REGISTERED_KEYS.add(key);
		FABRIC_DYNAMIC_REGISTRY_KEYS.add(key);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	static void onNewDatapackRegistries(NewDatapackRegistryEvent event) {
		for (RegistryDataLoader.RegistryData dynamicRegistry : PENDING_WORLD_REGISTRIES) {
			Codec networkCodec = NETWORK_CODECS.get(dynamicRegistry.key());
			event.worldRegistry((ResourceKey) dynamicRegistry.key(), dynamicRegistry.elementCodec(), networkCodec);
		}

		for (RegistryDataLoader.RegistryData dynamicRegistry : PENDING_RELOADABLE_REGISTRIES) {
			event.reloadableRegistry((ResourceKey) dynamicRegistry.key(), dynamicRegistry.elementCodec());
		}

		PENDING_WORLD_REGISTRIES.clear();
		PENDING_RELOADABLE_REGISTRIES.clear();
	}
}
