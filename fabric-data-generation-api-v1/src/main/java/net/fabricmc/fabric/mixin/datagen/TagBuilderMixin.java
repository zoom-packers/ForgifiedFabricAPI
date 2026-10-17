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

package net.fabricmc.fabric.mixin.datagen;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.UnmodifiableView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;

import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagBuilder;

/**
 * Implements {@link FabricTagBuilder} on {@link TagBuilder}. NeoForge already tracks tag removals
 * (and writes them to the generated tag file), so the removal methods simply delegate to NeoForge.
 */
@Mixin(TagBuilder.class)
public abstract class TagBuilderMixin implements FabricTagBuilder {
	@Shadow
	@Final
	private List<TagEntry> removeEntries;

	@Override
	public @UnmodifiableView List<TagEntry> getRemovals() {
		return Collections.unmodifiableList(this.removeEntries);
	}
}
