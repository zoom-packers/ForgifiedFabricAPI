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

package net.fabricmc.fabric.impl.item;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * Bridges {@link net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry} to NeoForge's tooltip appenders.
 * Ordering relative to vanilla components is handled by NeoForge, so upstream's {@code VanillaTooltipProviderOrder}
 * and {@code ItemStack} mixin are not needed.
 */
public final class ItemComponentTooltipProviderRegistryImpl {
	private static final List<TooltipPair<?>> first = new ArrayList<>();
	private static final List<TooltipPair<?>> last = new ArrayList<>();
	private static final Map<DataComponentType<?>, List<TooltipPair<?>>> before = new IdentityHashMap<>();
	private static final Map<DataComponentType<?>, List<TooltipPair<?>>> after = new IdentityHashMap<>();

	public static void register(RegisterTooltipAppendersEvent event) {
		for (TooltipPair<?> pair : first) {
			event.registerComponentAppenderBeforeAll(pair.component(), pair.createAppender());
		}

		for (TooltipPair<?> pair : last) {
			event.registerComponentAppenderAfterAll(pair.component(), pair.createAppender());
		}

		before.forEach((anchor, pairs) ->
				pairs.forEach(pair ->
						event.registerComponentAppenderBefore(pair.component(), anchor, pair.createAppender())));
		after.forEach((anchor, pairs) ->
				pairs.forEach(pair ->
						event.registerComponentAppenderAfter(pair.component(), anchor, pair.createAppender())));
	}

	public static <T> void addFirst(DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
		first.add(new TooltipPair<>(componentType, getter));
	}

	public static <T> void addLast(DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
		last.add(new TooltipPair<>(componentType, getter));
	}

	public static <T> void addBefore(DataComponentType<?> anchor, DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
		before.computeIfAbsent(anchor, k -> new ArrayList<>()).add(new TooltipPair<>(componentType, getter));
	}

	public static <T> void addAfter(DataComponentType<?> anchor, DataComponentType<T> componentType, TooltipProvider.Getter<T> getter) {
		after.computeIfAbsent(anchor, k -> new ArrayList<>()).add(new TooltipPair<>(componentType, getter));
	}

	private record TooltipPair<T>(DataComponentType<T> component, TooltipProvider.Getter<T> getter) {
		private TooltipAppender createAppender() {
			return TooltipAppender.createComponentAppender(this.component, this.getter);
		}
	}
}
